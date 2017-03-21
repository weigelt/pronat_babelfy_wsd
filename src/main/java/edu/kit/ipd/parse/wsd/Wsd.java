package edu.kit.ipd.parse.wsd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.ipd.parse.luna.agent.AbstractAgent;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.graph.IGraph;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.graph.Pair;
import edu.kit.ipd.parse.wsd.utils.GraphUtils;
import it.uniroma1.lcl.babelfy.commons.BabelfyConfiguration;
import it.uniroma1.lcl.babelfy.commons.BabelfyParameters;
import it.uniroma1.lcl.babelfy.commons.BabelfyParameters.MCS;
import it.uniroma1.lcl.babelfy.commons.BabelfyParameters.MatchingType;
import it.uniroma1.lcl.babelfy.commons.BabelfyParameters.ScoredCandidates;
import it.uniroma1.lcl.babelfy.commons.BabelfyParameters.SemanticAnnotationResource;
import it.uniroma1.lcl.babelfy.commons.BabelfyParameters.SemanticAnnotationType;
import it.uniroma1.lcl.babelfy.commons.BabelfyToken;
import it.uniroma1.lcl.babelfy.commons.PosTag;
import it.uniroma1.lcl.babelfy.commons.annotation.SemanticAnnotation;
import it.uniroma1.lcl.babelfy.core.Babelfy;
import it.uniroma1.lcl.babelnet.BabelNet;
import it.uniroma1.lcl.babelnet.BabelNetConfiguration;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetID;
import it.uniroma1.lcl.babelnet.InvalidBabelSynsetIDException;
import it.uniroma1.lcl.jlt.util.Language;

/**
 * Agent to add Word Sense Disambiguation information to the identified nouns
 * from the babelfy toolkit
 *
 * @author Tobias Hey
 *
 */

@MetaInfServices(AbstractAgent.class)
public class Wsd extends AbstractAgent {

	private Babelfy bfy;

	private BabelNet bn;

	private static final String ID = "wsd";

	private static final Logger logger = LoggerFactory.getLogger(Wsd.class);

	private HashMap<String, PosTag> posTags;

	private HashMap<String, BabelSynset> synsets;

	/*
	 * (non-Javadoc)
	 *
	 * @see edu.kit.ipd.parse.luna.agent.LunaObserver#init()
	 */
	@Override
	public void init() {
		setId(ID);
		createPOSTags();
		//		try {
		BabelfyConfiguration bfc = null;
		BabelNetConfiguration bc = null;
		InputStream isFy = Wsd.class.getResourceAsStream("/config/babelfy.properties");
		InputStream isNet = Wsd.class.getResourceAsStream("/config/babelnet.properties");
		File tempFy;
		File tempNet;
		try {
			tempFy = File.createTempFile("tempFy", ".tmp");
			tempNet = File.createTempFile("tempNet", ".tmp");
			FileUtils.copyInputStreamToFile(isFy, tempFy);
			FileUtils.copyInputStreamToFile(isNet, tempNet);
			bfc.CONFIG_FILE = tempFy.getPath();
			bc.CONFIG_FILE = tempNet.getPath();
			bfc = BabelfyConfiguration.getInstance();
			bc = BabelNetConfiguration.getInstance();
			bfc.setConfigurationFile(tempFy);
			bc.setConfigurationFile(tempNet);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//			bc.setBasePath(Wsd.class.getResource("/config/").toURI().getPath());
		//		} catch (URISyntaxException e) {
		//			// TODO Auto-generated catch block
		//			e.printStackTrace();
		//		}
		bn = BabelNet.getInstance();
		BabelfyParameters bp = new BabelfyParameters();
		bp.setAnnotationResource(SemanticAnnotationResource.WN);
		bp.setAnnotationType(SemanticAnnotationType.ALL);
		bp.setDensestSubgraph(true);
		bp.setExtendCandidatesWithAIDAmeans(true);
		bp.setMatchingType(MatchingType.PARTIAL_MATCHING);
		bp.setMCS(MCS.OFF);
		bp.setScoredCandidates(ScoredCandidates.ALL);
		bfy = new Babelfy(bp);
		synsets = new HashMap<>();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see edu.kit.ipd.parse.luna.agent.AbstractAgent#exec()
	 */
	@Override
	public void exec() {
		List<INode> utterance = new ArrayList<INode>();
		try {
			utterance = GraphUtils.getNodesOfUtterance(graph);
		} catch (MissingDataException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		List<BabelfyToken> bfTokens = prepareInput(utterance);

		List<SemanticAnnotation> bfyAnnotations = bfy.babelfy(bfTokens, Language.EN);
		int[] previousToken = new int[] { -1, -1 };
		List<INode> nodes = null;
		List<Pair<BabelSynset, Double>> senses = null;
		List<Pair<BabelSynset, Double>> sequenceSenses = new ArrayList<>();

		for (SemanticAnnotation semanticAnnotation : bfyAnnotations) {
			// if change in Token range
			if (!(semanticAnnotation.getTokenOffsetFragment().getEnd() == previousToken[1])
					|| !(semanticAnnotation.getTokenOffsetFragment().getStart() == previousToken[0])) {
				// if change from sequence to single Token p.e. (10,11) -> (11,11) save sequence tokens else reset them
				if (semanticAnnotation.getTokenOffsetFragment().getEnd() == previousToken[1]) {
					sequenceSenses = senses;
				} else {
					sequenceSenses = new ArrayList<>();
				}
				// if change write previous senses
				if (senses != null && nodes != null) {
					writeToNodes(nodes, senses, graph);
				}
				nodes = getNodesForAnnotation(semanticAnnotation, utterance);
				senses = new ArrayList<>();
			}

			// if sequence tokens present use them as sense
			if (!sequenceSenses.isEmpty()) {
				senses = sequenceSenses;
			} else {
				// if score is higher than 30% or sense spans over several tokens (sequence)
				if (semanticAnnotation.getScore() > 0.3d || semanticAnnotation.getTokenOffsetFragment().getEnd()
						- semanticAnnotation.getTokenOffsetFragment().getStart() > 0) {
					try {
						BabelSynset synset;
						String synsetID = semanticAnnotation.getBabelSynsetID();
						if (synsetID.endsWith("n")) {

							if (synsets.containsKey(synsetID)) {
								synset = synsets.get(synsetID);
							} else {

								synset = bn.getSynset(new BabelSynsetID(synsetID));
								synsets.put(synsetID, synset);
							}

							senses.add(new Pair<BabelSynset, Double>(synset, semanticAnnotation.getScore()));
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InvalidBabelSynsetIDException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
			previousToken[0] = semanticAnnotation.getTokenOffsetFragment().getStart();
			previousToken[1] = semanticAnnotation.getTokenOffsetFragment().getEnd();
		}

		if (senses != null && nodes != null) {
			writeToNodes(nodes, senses, graph);
		}
	}

	private void writeToNodes(List<INode> nodes, List<Pair<BabelSynset, Double>> senses, IGraph graph) {
		if (!senses.isEmpty()) {
			Collections.sort(senses, new SenseComparator());
			Collections.reverse(senses);
			List<Pair<String, Double>> results = new ArrayList<>();
			for (Pair<BabelSynset, Double> pair : senses) {
				results.add(new Pair<String, Double>(pair.getLeft().getId().getID(), pair.getRight()));
			}
			if (graph.hasNodeType("token")) {
				if (!graph.getNodeType("token").containsAttribute("wnSynsetID", "String")) {
					graph.getNodeType("token").addAttributeToType("String", "wnSynsetID");
				}
				if (!graph.getNodeType("token").containsAttribute("bnSynsetID", "String")) {
					graph.getNodeType("token").addAttributeToType("String", "bnSynsetID");
				}
				if (!graph.getNodeType("token").containsAttribute("bnScore", "Double")) {
					graph.getNodeType("token").addAttributeToType("Double", "bnScore");
				}
				if (!graph.getNodeType("token").containsAttribute("bnGloss", "String")) {
					graph.getNodeType("token").addAttributeToType("String", "bnGloss");
				}
				if (!graph.getNodeType("token").containsAttribute("bfyResults", results.getClass().getName())) {
					graph.getNodeType("token").addAttributeToType(results.getClass().getName(), "bfyResults");
				}
				for (INode node : nodes) {
					if (!Objects.equals(node.getAttributeValue("wnSynsetID"), senses.get(0).getLeft().getWordNetOffsets().get(0).getID())) {
						node.setAttributeValue("wnSynsetID", senses.get(0).getLeft().getWordNetOffsets().get(0).getID());
					}
					if (!Objects.equals(node.getAttributeValue("bnSynsetID"), senses.get(0).getLeft().getId().getID())) {
						node.setAttributeValue("bnSynsetID", senses.get(0).getLeft().getId().getID());
					}
					if (!Objects.equals(node.getAttributeValue("bnScore"), senses.get(0).getRight())) {
						node.setAttributeValue("bnScore", senses.get(0).getRight());
					}
					if (node.getAttributeValue("bfyResults") != null) {
						@SuppressWarnings("unchecked")
						List<Pair<String, Double>> graphResults = (List<Pair<String, Double>>) node.getAttributeValue("bfyResults");
						if (results != null) {
							if (!resultsEquals(results, graphResults)) {
								node.setAttributeValue("bfyResults", results);
							}
						} else {
							node.setAttributeValue("bfyResults", results);
						}
					} else {
						node.setAttributeValue("bfyResults", results);
					}
					try {
						if (!Objects.equals(node.getAttributeValue("bnGloss"), senses.get(0).getLeft().getGlosses().get(0).getGloss())) {
							node.setAttributeValue("bnGloss", senses.get(0).getLeft().getGlosses().get(0).getGloss());
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			} else {
				logger.error("Graph has no Token Nodes!");
			}
		}

	}

	private boolean resultsEquals(List<Pair<String, Double>> first, List<Pair<String, Double>> second) {
		if (first.size() != second.size()) {
			return false;
		}
		for (Pair<String, Double> pairSecond : second) {
			boolean match = false;
			for (Pair<String, Double> pairFirst : first) {
				if (pairFirst.getLeft().equals(pairSecond.getLeft())
						&& Math.abs(pairFirst.getRight() - pairSecond.getRight()) < 0.0000001d) {
					match = true;
				}
			}
			if (!match) {
				return false;
			}
		}
		return true;
	}

	private List<BabelfyToken> prepareInput(List<INode> utterance) {
		List<BabelfyToken> bfTokens = new ArrayList<>();
		for (INode iNode : utterance) {
			if (iNode.getType().getName().equals("token")) {
				BabelfyToken token = new BabelfyToken((String) iNode.getAttributeValue("value"));
				if (iNode.getAttributeNames().contains("pos") && !iNode.getAttributeValue("pos").equals("")) {
					if (posTags.containsKey(iNode.getAttributeValue("pos"))) {
						token.setPostag(posTags.get(iNode.getAttributeValue("pos")));
					} else {
						token.setPostag(PosTag.OTHER);
						token.setLanguage(Language.EN);
					}

				}
				bfTokens.add(token);
			}
		}
		return bfTokens;
	}

	private List<INode> getNodesForAnnotation(SemanticAnnotation semanticAnnotation, List<INode> utterance) {
		List<INode> result = new ArrayList<>();
		int start = semanticAnnotation.getTokenOffsetFragment().getStart();
		result.add(utterance.get(start));
		while (start < semanticAnnotation.getTokenOffsetFragment().getEnd()) {
			start++;
			result.add(utterance.get(start));
		}
		return result;
	}

	/**
	 * Creates a mapping between WordNet POS Tags (simple) and the POS Tags used
	 * in Penn Treebank tagset.
	 *
	 * @return a HashMap mapping composite Tags (e.g. JJ, NNP, VB, etc.) with
	 *         simple Tags (e.g. Adjective, Noun, Verb, etc.)
	 */
	private HashMap<String, PosTag> createPOSTags() {
		posTags = new HashMap<String, PosTag>();

		// Available types: POS.ADJECTIVE, POS.ADVERB, POS.NOUN, POS.VERB

		//Keys: POS Tag, Values: POS

		posTags.put("JJ", PosTag.ADJECTIVE);
		posTags.put("JJR", PosTag.ADJECTIVE);
		posTags.put("JJS", PosTag.ADJECTIVE);
		posTags.put("NN", PosTag.NOUN);
		posTags.put("NNP", PosTag.NOUN);
		posTags.put("NNPS", PosTag.NOUN);
		posTags.put("NNS", PosTag.NOUN);
		posTags.put("RB", PosTag.ADVERB);
		posTags.put("RBR", PosTag.ADVERB);
		posTags.put("RBS", PosTag.ADVERB);
		posTags.put("VB", PosTag.VERB);
		posTags.put("VBD", PosTag.VERB);
		posTags.put("VBG", PosTag.VERB);
		posTags.put("VBN", PosTag.VERB);
		posTags.put("VBP", PosTag.VERB);
		posTags.put("VBZ", PosTag.VERB);

		return posTags;
	}

	private class SenseComparator implements Comparator<Pair<BabelSynset, Double>> {

		@Override
		public int compare(Pair<BabelSynset, Double> o1, Pair<BabelSynset, Double> o2) {
			return Double.compare(o1.getRight(), o2.getRight());
		}

	}

}
