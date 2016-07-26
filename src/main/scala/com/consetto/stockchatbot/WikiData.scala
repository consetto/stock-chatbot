package com.consetto.stockchatbot

import dispatch._, Defaults._
import argonaut._, Argonaut._
import scala.concurrent.Await
import scala.concurrent.duration._
import com.ning.http.client.Response
import scala.collection.JavaConversions._

sealed trait SymbolType
case object ISIN extends SymbolType
case object Symbol extends SymbolType

object WikiData {

  /**
    * Main function: Find a quote symbol by company name in Wikidata
    */
  def findSymbol(name: String): Future[Option[(Option[String], Option[String])]] =
    lookup(name) flatMap {
      case Some(rsp) =>
        Future.sequence(rsp.search.map { x => getSymbolFromResource(x.id) })
      case None =>
        Future { Nil }
    } map { xs =>
      xs.collect {
        case (Some(x), Some(y)) => (Some(x), Some(y))
        case (Some(x), None) => (Some(x), None)
        case (None, Some(y)) => (None, Some(y))
      }.headOption
    } recover {
      case e: Exception => None
    }

  // ==================================================
  // Search for resource by name

  def lookup(name: String) = Http (
    url(s"""https://www.wikidata.org/w/api.php""") <<? Map(
      "action" -> "wbsearchentities",
      "search" -> name,
      "format" -> "json",
      "language" -> "en",
      "type" -> "item",
      "continue" -> "0"
    ) <:< Seq("Accept" -> "application/json") OK asLookupResponse
  )

  protected def asLookupResponse(rsp: Response) =
    Parse.decodeOption[LookupResponse](rsp.getResponseBody)

  case class LookupResponse(search: List[LookupSearchItem])
  case class LookupSearchItem(id: String)

  implicit def LookupResponseCodec: CodecJson[LookupResponse] =
    casecodec1(LookupResponse.apply, LookupResponse.unapply)("search")

  implicit def LookupSearchItemCodec: CodecJson[LookupSearchItem] =
    casecodec1(LookupSearchItem.apply, LookupSearchItem.unapply)("id")

  // ==================================================
  // Load a resource

  protected def resource(id: String) = Http (
    url(s"""https://www.wikidata.org/wiki/Special:EntityData/${id}.json""") <:< Seq("Accept" -> "application/json") OK asJson
  )

  protected def asJson(rsp: Response) =
    Parse.parseOption(rsp.getResponseBody)

  // ==================================================
  // Extract symbols from a resource

  protected def getSymbolFromResource(id: String) = resource(id).map {
    case Some(json) =>
      val symbol = getSymbolFromResourceP414(id, json) orElse getSymbolFromResourceP249(id, json)
      val isin = getSymbolFromResourceISIN(id, json)
      (isin.map(_._1), symbol.map(_._1))
    case None =>
      (None, None)
  }

  private def getSymbolFromResourceP249(id: String, json: Json): Option[(String, SymbolType)] = {
    val cursor = json.cursor
    for {
      entities <- cursor.downField("entities")
      resource <- entities.downField(id)
      claims <- resource.downField("claims")
      tickerSymbols <- claims.downField("P249")
      firstTickerSymbol <- tickerSymbols.downArray
      dataValue <- firstTickerSymbol.downField("datavalue")
      value <- dataValue.downField("value")
      valueStr <- value.focus.string
    } yield (valueStr, Symbol)
  }

  private def getSymbolFromResourceISIN(id: String, json: Json): Option[(String, SymbolType)] = {
    val cursor = json.cursor
    for {
      entities <- cursor.downField("entities")
      resource <- entities.downField(id)
      claims <- resource.downField("claims")
      tickerSymbols <- claims.downField("P946")
      firstTickerSymbol <- tickerSymbols.downArray
      mainsnak <- firstTickerSymbol.downField("mainsnak")
      dataValue <- mainsnak.downField("datavalue")
      value <- dataValue.downField("value")
      valueStr <- value.focus.string
    } yield (valueStr, ISIN)
  }

  private def getSymbolFromResourceP414(id: String, json: Json): Option[(String, SymbolType)] = {
    val cursor = json.cursor
    for {
      entities <- cursor.downField("entities")
      resource <- entities.downField(id)
      claims <- resource.downField("claims")
      stockExchange <- claims.downField("P414")
      firstStockExchange <- stockExchange.downArray
      qualifiers <- firstStockExchange.downField("qualifiers")
      tickerSymbols <- qualifiers.downField("P249")
      firstTickerSymbol <- tickerSymbols.downArray
      dataValue <- firstTickerSymbol.downField("datavalue")
      value <- dataValue.downField("value")
      valueStr <- value.focus.string
    } yield (valueStr, Symbol)
  }
}
