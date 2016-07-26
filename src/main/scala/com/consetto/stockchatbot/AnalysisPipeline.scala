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

object AnalysisPipeline {

  val segmenter = new StanfordSegmenter().
    config(
      _.fallbackLanguage := Some("en")
    )

  val posTagger = new StanfordPosTagger().
    config(
      _.model := SharedBinding[MaxentTaggerResource]("edu/stanford/nlp/models/pos-tagger/german/german-fast.tagger")
    )

  val parser = new StanfordParser().
    config(
      _.model := SharedBinding[StanfordParserGrammerResource]("edu/stanford/nlp/models/srparser/germanSR.ser.gz")
    )

  val namedEntityTagger = new StanfordNer().
    config(
      _.model := SharedBinding[StanfordNerResource]("edu/stanford/nlp/models/ner/german.dewac_175m_600.crf.ser.gz")
    )

  val companyNameAnnotator = new CompanyNameCandidateAnnotator()

  def extractNer(cas: JCas): (String, Vector[String]) = {
    (cas.getDocumentText, cas.select[CompanyNameCandidate].map { c =>
      c.getCoveredText
    }.toVector)
  }

  def pipeline[F[_]] = (
    casFromText[F] andThen
      annotate[F](segmenter) andThen
      annotate[F](posTagger) andThen
      annotate[F](parser) andThen
      annotate[F](namedEntityTagger) andThen
      annotate[F](companyNameAnnotator) andThen
      extractCas[F, (String, Vector[String])](extractNer _)
  )
}
