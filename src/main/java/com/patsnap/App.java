package com.patsnap;

import org.apache.ctakes.assertion.medfacts.cleartk.*;
import org.apache.ctakes.chunker.ae.Chunker;
import org.apache.ctakes.chunker.ae.adjuster.ChunkAdjuster;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory;
import org.apache.ctakes.constituency.parser.ae.ConstituencyParser;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.SentenceDetector;
import org.apache.ctakes.core.ae.SimpleSegmentAnnotator;
import org.apache.ctakes.core.ae.TokenizerAnnotatorPTB;
import org.apache.ctakes.core.pipeline.PipelineBuilder;
import org.apache.ctakes.core.pipeline.PiperFileReader;
import org.apache.ctakes.dependency.parser.ae.ClearNLPDependencyParserAE;
import org.apache.ctakes.dictionary.lookup2.ae.DefaultJCasTermAnnotator;
import org.apache.ctakes.lvg.ae.LvgAnnotator;
import org.apache.ctakes.postagger.POSTagger;
import org.apache.ctakes.typesystem.type.constants.CONST;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Hello world!
 */
public class App {
  public static AnalysisEngineDescription getFastPipeline() throws ResourceInitializationException, MalformedURLException {
    AggregateBuilder builder = new AggregateBuilder();
    builder.add(getTokenProcessingPipeline());
    builder.add(DefaultJCasTermAnnotator.createAnnotatorDescription());
    //builder.add(ClearNLPDependencyParserAE.createAnnotatorDescription());
    builder.add(PolarityCleartkAnalysisEngine.createAnnotatorDescription());
    builder.add(UncertaintyCleartkAnalysisEngine.createAnnotatorDescription());
    builder.add(HistoryCleartkAnalysisEngine.createAnnotatorDescription());
    builder.add(ConditionalCleartkAnalysisEngine.createAnnotatorDescription());
    builder.add(GenericCleartkAnalysisEngine.createAnnotatorDescription());
    builder.add(SubjectCleartkAnalysisEngine.createAnnotatorDescription());
    return builder.createAggregateDescription();
  }

  public static AnalysisEngineDescription getParsingPipeline() throws ResourceInitializationException, MalformedURLException {
    AggregateBuilder builder = new AggregateBuilder();
    builder.add(getTokenProcessingPipeline());
    builder.add(ClearNLPDependencyParserAE.createAnnotatorDescription());
    builder.add(AnalysisEngineFactory.createEngineDescription(ConstituencyParser.class));
    return builder.createAggregateDescription();
  }

  public static AnalysisEngineDescription getTokenProcessingPipeline() throws ResourceInitializationException, MalformedURLException {
    AggregateBuilder builder = new AggregateBuilder();
    builder.add(SimpleSegmentAnnotator.createAnnotatorDescription());
    builder.add(SentenceDetector.createAnnotatorDescription());
    builder.add(TokenizerAnnotatorPTB.createAnnotatorDescription());
    builder.add(LvgAnnotator.createAnnotatorDescription());
    builder.add(ContextDependentTokenizerAnnotator.createAnnotatorDescription());
    builder.add(POSTagger.createAnnotatorDescription());
    return builder.createAggregateDescription();
  }

  public static AnalysisEngineDescription getNpChunkerPipeline() throws ResourceInitializationException {
    AggregateBuilder builder = new AggregateBuilder();
    builder.add(Chunker.createAnnotatorDescription());
    builder.add(getStandardChunkAdjusterAnnotator());
    builder.add(AnalysisEngineFactory.createEngineDescription(ClinicalPipelineFactory.CopyNPChunksToLookupWindowAnnotations.class));
    builder.add(AnalysisEngineFactory.createEngineDescription(ClinicalPipelineFactory.RemoveEnclosedLookupWindows.class));
    return builder.createAggregateDescription();
  }

  public static AnalysisEngineDescription getStandardChunkAdjusterAnnotator() throws ResourceInitializationException {
    AggregateBuilder builder = new AggregateBuilder();
    // adjust NP in NP NP to span both
    builder.add(ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "NP"}, 1));
    // adjust NP in NP PP NP to span all three
    builder.add(ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "PP", "NP"}, 2));
    return builder.createAggregateDescription();
  }

  static void test() throws IOException, UIMAException, SAXException {
    final String note = "History of diabetes and hypertension."
        + " Mother had breast cancer."
        + " Sister with multiple sclerosis."
        + " The patient is suffering from extreme pain due to shark bite."
        + " Recommend continuing use of aspirin, oxycodone, and coumadin."
        + " Continue exercise for obesity and hypertension."
        + " Patient denies smoking and chest pain."  // Space between sentences introduced " Patient"
        + " Patient has no cancer."
        + " There is no sign of multiple sclerosis."
        + " Mass is suspicious for breast cancer."
        + " Possible breast cancer."
        + " Cannot exclude stenosis."
        + " Some degree of focal pancreatitis is also possible."
        + " Discussed surgery and chemotherapy."  // Space between sentences introduced " Discussed"
        + " Will return if pain continues.";
    final JCas jcas = JCasFactory.createJCas();
    jcas.setDocumentText(note);
    // final AnalysisEngineDescription aed = getFastPipeline();
    final AnalysisEngineDescription aed = getTokenProcessingPipeline();

    SimplePipeline.runPipeline(jcas, aed);
    // final boolean printCuis = Arrays.asList(args ).contains("cuis" );
    final Collection<String> codes = new ArrayList<>();
    for (IdentifiedAnnotation entity : JCasUtil.select(jcas, IdentifiedAnnotation.class)) {

      System.out.println("Entity: " + entity.getCoveredText()
                             + " === Polarity: " + entity.getPolarity()
                             + " === Uncertain? " + (entity.getUncertainty() == CONST.NE_UNCERTAINTY_PRESENT)
                             + " === Subject: " + entity.getSubject()
                             + " === Generic? " + (entity.getGeneric() == CONST.NE_GENERIC_TRUE)
                             + " === Conditional? " + (entity.getConditional() == CONST.NE_CONDITIONAL_TRUE)
                             + " === History? " + (entity.getHistoryOf() == CONST.NE_HISTORY_OF_PRESENT)
      );
    }
  }

  public static void main(String[] args) {
    try {
      String PIPER_FILE_PATH = "hello.piper";
      String DOC_TEXT = "This is a test, a diabetes";
      PiperFileReader reader = new PiperFileReader(PIPER_FILE_PATH);
      PipelineBuilder builder = reader.getBuilder();
      PipelineBuilder b = builder.run(DOC_TEXT);
      System.out.printf(b.getAeNames().toString());
      test();
    } catch (SAXException| IOException | UIMAException multE) {
      System.out.println(multE.toString());
    }

  }
}
