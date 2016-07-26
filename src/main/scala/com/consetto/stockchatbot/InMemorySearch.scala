package com.consetto.stockchatbot

import org.apache.lucene.store.RAMDirectory
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import scala.io.Source
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.document.{TextField, StringField}
import org.apache.lucene.index.DirectoryReader
import scala.collection.JavaConversions._
import com.github.tototoshi.csv._
import java.io.File
import org.apache.lucene.util.QueryBuilder
import org.apache.lucene.search.{TermQuery, BooleanQuery, BooleanClause}
import org.apache.lucene.index.Term
import org.apache.lucene.search.TopScoreDocCollector

object InMemorySearch {

  // The RAM Directory stores the data in memory
  private val idx = new RAMDirectory()

  /**
    * Fill the RAM Directory with data from a CSV file
    * @param file The path of the CSV file
    */
  def loadStockData(file: String) = {
    val writer = new IndexWriter(idx, new IndexWriterConfig(new StandardAnalyzer()))

    // Read the csv file
    val reader = CSVReader.open(new File(file))

    // Iterate over the CSV file and craete a Lucence document for every line
    val docs = asJavaIterable(reader.iterator.map { line =>
      val symbol = line(0)
      val name = line(1)
      val exchange = line(2)

      val doc = new Document
      doc.add(new TextField("symbol", symbol, Field.Store.YES))
      doc.add(new TextField("name", name, Field.Store.YES))
      doc.add(new StringField("exchange", exchange, Field.Store.YES))

      doc
    }.toIterable)

    // Add the documents the ram directory
    writer.addDocuments(docs)

    writer.flush
    writer.commit
    writer.close
  }

  /**
    * Search in the RAM Directory
    * @param text The text we want to search
    * @param hits The maximum number of hits that is returned
    */
  def search(text: String, hits: Int): Seq[(String, String, String)] = {
    try {
      val searcher = new IndexSearcher(DirectoryReader.open(idx))
      val collector = TopScoreDocCollector.create(hits);

      // Build the query
      val query1 = (new QueryBuilder(new StandardAnalyzer())).createMinShouldMatchQuery("name", text, 0.05f)
      val query2 = (new QueryBuilder(new StandardAnalyzer())).createPhraseQuery("symbol", text)
      val query = new BooleanQuery.Builder().
        add(query1, BooleanClause.Occur.SHOULD).
        add(query2, BooleanClause.Occur.SHOULD).
        build()

      // Search and extract the data
      searcher.search(query1, collector)
      collector.topDocs().scoreDocs.map { hit =>
        val docId = hit.doc
        val doc = searcher.doc(docId)
        (doc.get("symbol"), doc.get("name"), doc.get("exchange"))
      }
    } catch {
      case e: Exception =>
        println(e.getMessage)
        Nil
    }
  }
}
