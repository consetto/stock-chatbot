package com.consetto.stockchatbot

import com.github.jenshaase.uimascala.core._
import com.github.jenshaase.uimascala.typesystem._
import org.apache.uima.jcas.JCas
import scala.collection.JavaConversions._

class CompanyNameCandidateAnnotator extends SCasAnnotator_ImplBase {

  def process(cas: JCas) = {
    // All Named entities are candidates
    annotateNamedEntities(cas)

    // All Nominal Phrases or prepositional phrases where all children are tokens
    annotatedNominalandPrepositionalPhrases(cas)

    // Add all tokens tagged as NE
    annotateNamedEntityPartOfSpeechTag(cas)
  }

  protected def annotateNamedEntities(cas: JCas) =
    cas.select[NamedEntity]. // Named Entities have been annotated by the Stanford NER annotator
      foreach { ner =>       // For each Named Entity we will create a CompanyNameCandidate
        val candidate = new CompanyNameCandidate(cas, ner.getBegin, ner.getEnd)
        candidate.setValue("NER-" + ner.getValue)
        add(candidate)
      }

  protected def annotatedNominalandPrepositionalPhrases(cas: JCas) =
    cas.select[Constituent]. // Constituents have been annotated by the Stanford Parser
      filter { c =>          // Only use NPs and PPs where the childrens are words
        (c.getConstituentType == "NP" || c.getConstituentType == "PP") &&
        (c.getChildren.toArray.forall(_.isInstanceOf[Token]))
      }.
      foreach { c =>        // For each NP or PP create a CompanyName Candidate
        getNominalTokens(cas.select[Token].toSeq).foreach { tokens => 
          val begin = tokens.head.getBegin
          val end = tokens.last.getEnd

          val covered = cas.selectCovered[CompanyNameCandidate](begin, end)
          if (!covered.exists(x => x.getBegin == begin && x.getEnd == end)) {
            // Only add this candidate is not already added (e.g. by another annotation method)
            val candidate = new CompanyNameCandidate(cas, begin, end)
            candidate.setValue(c.getConstituentType)
            add(candidate)
          }
        }
      }

  protected def annotateNamedEntityPartOfSpeechTag(cas: JCas) =
    cas.select[Token]. // Tokens have been annotated the Stanford Segmenter
      filter { t => t.getPos.getName == "NE" }. // only use tokens where the POS is NE
      foreach { t => // Create the candidate, if it does not already exists
        val begin = t.getBegin
        val end = t.getEnd

        val covered = cas.selectCovered[CompanyNameCandidate](t)
        if (!covered.exists(x => x.getBegin == begin && x.getEnd == end)) {
          val candidate = new CompanyNameCandidate(cas, begin, end)
          candidate.setValue("NE")
          add(candidate)
        }
      }

  protected def getNominalTokens(tokens: Seq[Token]): Seq[Seq[Token]] = {
    if (tokens.size == 0) {
      Seq()
    } else {
      val npTokens = tokens.takeWhile(_.getPos.getName.startsWith("N"))
      val rest = tokens.drop(npTokens.size).dropWhile { x => !x.getPos.getName.startsWith("N") }
      if (npTokens.size > 0) {
        Seq(npTokens) ++ getNominalTokens(rest)
      } else {
        getNominalTokens(rest)
      }
    }
  }
}
