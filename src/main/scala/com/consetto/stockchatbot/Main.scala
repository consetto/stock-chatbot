package com.consetto.stockchatbot

import com.github.jenshaase.uimascala.core._
import com.github.jenshaase.uimascala.core.stream._
import com.github.jenshaase.uimascala.core.configuration._
import com.github.jenshaase.uimascala.typesystem._
import com.github.jenshaase.uimascala.ner._
import com.github.jenshaase.uimascala.segmenter._
import com.github.jenshaase.uimascala.pos._
import com.github.jenshaase.uimascala.parser._
import org.apache.uima.jcas.JCas
import scala.collection.JavaConversions._
import fs2._
import fs2.io
import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App {

  import YahooFinanceApi._

  def createResponse(ners: Vector[String]): String = {
    val data = ners.
      map(x => (x, SymbolFinder.find(x))).
      collect { case (ner, Some((symbol, value))) => (ner, symbol, value) }.
      sortBy(- _._3).
      toList

    data match {
      case (ner, symbol, value) :: _ if (value >= 0.5) =>
        try {
          val data = Await.result(YahooFinanceApi.getQuoteData(symbol), 10 seconds)
          data match {
            case Some(QouteDataResult(QueryData(_, _, _, QuoteResult(QuoteData(_, ask, _, currency, _, _, _))))) =>
              s"""Der Kurs von ${ner} (${symbol}) beträgt aktuell: ${ask} ${currency}"""
            case _ =>
              s"""Ich konnte den Kurs von ${ner} (${symbol}) nicht ermitteln"""
          }
        } catch {
          case e: Exception =>
            s"""Ich konnte den Kurs von ${ner} (${symbol}) nicht ermitteln"""
        }
      case Nil =>
        "Ich bin mir nicht sicher was du meinst?"
      case xs =>
        val unsureStr = xs.map { x => x._2 + " (" + x._1 + ")" }.mkString(", ")
        s"""Ich bin mir nicht sicher was du meinst? Meinst du eins hiervon: ${unsureStr}?"""
    }
  }

  def run = {
    InMemorySearch.loadStockData("src/main/resources/Stock.csv")
    val analysisResult = Stream.pure(TestData.data:_*).through(AnalysisPipeline.pipeline).toList
    analysisResult.foreach { case (sentence, ners) =>
      println(sentence)
      println("  => " + createResponse(ners))
    }
    dispatch.Http.shutdown()
  }
  
  run
}
