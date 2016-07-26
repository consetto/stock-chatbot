package com.consetto.stockchatbot

import scala.concurrent.Await
import scala.concurrent.duration._

object SymbolFinder {

  def find(entity: String): Option[(String, Double)] =
    try {
      (Await.result(WikiData.findSymbol(entity), 10 seconds) match {
        case Some((Some(isin), Some(symbol))) =>
          val isinSymbol = if (isin.startsWith("US")) {
            None
          } else {
            Await.result(YahooFinanceApi.isinToSymbol(isin), 10 seconds)
          }

          isinSymbol.map(x => (x, 1.0)) orElse {
            Await.result(YahooFinanceApi.findSymbol(symbol, 5), 10 seconds).flatMap { xs =>
              xs.headOption.map(x => (x._1, 0.9))
            }
          }

        case Some((Some(isin), None)) =>
          val isinSymbol = if (isin.startsWith("US")) {
            None
          } else {
            Await.result(YahooFinanceApi.isinToSymbol(isin), 10 seconds)
          }
          isinSymbol.map(x => (x, 1.0)) orElse {
            Await.result(YahooFinanceApi.findSymbol(isin, 5), 10 seconds).flatMap { xs =>
              xs.headOption.map(x => (x._1, 0.8))
            }
          }

        case Some((None, Some(symbol))) =>
          Await.result(YahooFinanceApi.findSymbol(symbol, 5), 10 seconds).flatMap { xs =>
            xs.headOption.map(x => (x._1, 0.7))
          }

        case _ =>
          Await.result(YahooFinanceApi.findSymbol(entity, 5), 10 seconds).flatMap { xs =>
            xs.headOption.map(x => (x._1, 0.2))
          } orElse {
            InMemorySearch.search(entity, 1).headOption.map { x => (x._1, 0.1) }
          }
      }).map {
        case (a, b) if (a.endsWith(".DE")) => (a.replace(".DE", ".F"), b)
        case (a, b) => (a, b)
      }
    } catch {
      case e: Exception => None
    }

}
