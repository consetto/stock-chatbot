package com.consetto.stockchatbot

import dispatch._, Defaults._
import com.ning.http.client.Response
import scala.concurrent.Await
import scala.concurrent.duration._
import org.jsoup._
import scala.collection.JavaConversions._
import argonaut._, Argonaut._

object YahooFinanceApi {

  /**
    * Find symbols by company name. This is similar to what
    * Yahoo-Ticker-Symbol-Downloader is doing.
    * @param name The company name
    * @param n Maximal number of results
    */
  def findSymbol(companyName: String, n: Int) = Http(
    url("http://finance.yahoo.com/lookup/") <<? Map(
      "s" -> companyName,
      "t" -> "S",
      "bypass" -> "true"
    ) <:< Seq("Accept" -> "application/json") OK asLookupList(n)
  ) recover {
    case e: Exception => None
  }

  protected def asLookupList(n: Int)(rsp: Response) = {
    val html = rsp.getResponseBody
    val doc = Jsoup.parse(html)
    val tableBody = doc.select("table.yui-dt tbody").headOption
    tableBody.map(
      _.getElementsByTag("tr").map { row =>
        val tableDefs = row.getElementsByTag("td")
        val symbol = tableDefs(0).text()
        val name = tableDefs(1).text()
        (symbol, name)
      }.toVector.take(n)
    )
  }

  /**
    * Converts a ISIN to a stock symbol
    * @param isin The ISIN of the quote
    */
  def isinToSymbol(isin: String) = Http {
    url("https://query.yahooapis.com/v1/public/yql") <<? Map(
      "q" -> s"""select * from yahoo.finance.isin where symbol="${isin}"""",
      "format" -> "json",
      "env" -> "store://datatables.org/alltableswithkeys"
    ) OK asIsinResult
  } recover {
    case e: Exception => None
  }

  protected def asIsinResult(rsp: Response): Option[String] = {
    Parse.parseOption(rsp.getResponseBody) match {
      case Some(json) =>
        val cursor = json.cursor
        for {
          query <- cursor.downField("query")
          results <- query.downField("results")
          stock <- results.downField("stock")
          isin <- stock.downField("Isin")
          isinStr <- isin.focus.string
        } yield isinStr
      case None =>
        None
    }
  }

  /**
    * Gets the current quote data of a stock symbol
    * @param symbol The stock symbol
    */
  def getQuoteData(symbol: String) = Http {
    url("https://query.yahooapis.com/v1/public/yql") <<? Map(
      "q" -> s"""select * from yahoo.finance.quotes where symbol="${symbol}"""",
      "format" -> "json",
      "env" -> "store://datatables.org/alltableswithkeys"
    ) OK asStockData
  } recover {
    case e: Exception => None
  }

  protected def asStockData(rsp: Response) =
    Parse.decodeOption[QouteDataResult](rsp.getResponseBody)

  case class QouteDataResult(query: QueryData)
  case class QueryData(count: Int, created: String, lang: String, results: QuoteResult)
  case class QuoteResult(quote: QuoteData)
  case class QuoteData(symbol: String, ask: String, changeInPercent: String, currency: String, daysLow: String, daysHeight: String, stockExchange: String)

  implicit def QouteDataResultCodec: CodecJson[QouteDataResult] =
    casecodec1(QouteDataResult.apply, QouteDataResult.unapply)("query")

  implicit def QueryDataCodec: CodecJson[QueryData] =
    casecodec4(QueryData.apply, QueryData.unapply)("count", "created", "lang", "results")

  implicit def QuoteResultCodec: CodecJson[QuoteResult] =
    casecodec1(QuoteResult.apply, QuoteResult.unapply)("quote")

  implicit def QuoteDataCodec: CodecJson[QuoteData] =
    casecodec7(QuoteData.apply, QuoteData.unapply)("symbol", "Ask", "ChangeinPercent", "Currency", "DaysLow", "DaysHigh", "StockExchange")

}
