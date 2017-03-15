package edu.kit.ipd.parse.wsd.utils;
/**
 * 
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.graph.IArc;
import edu.kit.ipd.parse.luna.graph.IArcType;
import edu.kit.ipd.parse.luna.graph.IGraph;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.graph.INodeType;
import edu.kit.ipd.parse.luna.graph.ParseGraph;

/**
 * @author Tobias Hey
 *
 */
public final class GraphUtils {

	private static final String INSTRUCTION_NUMBER_VALUE_NAME = "instructionNumber";

	private static final String NEXT_ARCTYPE_NAME = "relation";

	private static final Logger logger = LoggerFactory.getLogger(GraphUtils.class);

	public static final List<INode> getNodesOfInstruction(IGraph graph, int instructionNumber) throws MissingDataException {
		ArrayList<INode> result = new ArrayList<>();
		INodeType tokenNodeType;
		if ((tokenNodeType = graph.getNodeType("token")) != null) {
			List<INode> utteranceNodes = getNodesOfUtterance(graph);
			for (INode node : utteranceNodes) {
				if (node.getType().equals(tokenNodeType)) {
					if (node.getAttributeValue(INSTRUCTION_NUMBER_VALUE_NAME).equals(instructionNumber)) {
						result.add(node);
					}
				}
			}
		} else {
			logger.error("Utterance Nodetype does not exist!");
			throw new MissingDataException("Utterance Nodetype does not exist!");
		}
		return result;

	}

	public static final List<INode> getNodesOfUtterance(IGraph graph) throws MissingDataException {
		ArrayList<INode> result = new ArrayList<>();
		IArcType nextArcType;
		if ((nextArcType = graph.getArcType("relation")) != null) {
			if (graph instanceof ParseGraph) {
				ParseGraph pGraph = (ParseGraph) graph;
				INode current = pGraph.getFirstUtteranceNode();
				List<? extends IArc> outgoingNextArcs = current.getOutgoingArcsOfType(nextArcType);
				boolean hasNext = !outgoingNextArcs.isEmpty();
				result.add(current);
				while (hasNext) {
					//assume that only one NEXT arc exists
					if (outgoingNextArcs.size() == 1) {
						current = outgoingNextArcs.toArray(new IArc[outgoingNextArcs.size()])[0].getTargetNode();
						result.add(current);
						outgoingNextArcs = current.getOutgoingArcsOfType(nextArcType);
						hasNext = !outgoingNextArcs.isEmpty();
					} else {
						logger.error("Nodes have more than one NEXT Arc");
						throw new IllegalArgumentException("Nodes have more than one NEXT Arc");
					}
				}
			} else {
				logger.error("Graph is no ParseGraph!");
				throw new MissingDataException("Graph is no ParseGraph!");
			}
		} else {
			logger.error("Next Arctype does not exist!");
			throw new MissingDataException("Next Arctype does not exist!");
		}
		return result;
	}

	public static final INode getNextNode(INode current, IGraph graph) {
		IArcType arcType = graph.getArcType(NEXT_ARCTYPE_NAME);
		List<? extends IArc> outgoingNextArcs = current.getOutgoingArcsOfType(arcType);
		if (!outgoingNextArcs.isEmpty()) {
			if (outgoingNextArcs.size() == 1) {
				return outgoingNextArcs.toArray(new IArc[outgoingNextArcs.size()])[0].getTargetNode();
			} else {
				logger.error("Nodes have more than one NEXT Arc");
				throw new IllegalArgumentException("Nodes have more than one NEXT Arc");
			}
		} else {
			return null;
		}
	}

	public static final INode getPreviousNode(INode current, IGraph graph) {
		IArcType arcType = graph.getArcType(NEXT_ARCTYPE_NAME);
		List<? extends IArc> incomingNextArcs = current.getIncomingArcsOfType(arcType);
		if (!incomingNextArcs.isEmpty()) {
			if (incomingNextArcs.size() == 1) {
				return incomingNextArcs.toArray(new IArc[incomingNextArcs.size()])[0].getSourceNode();
			} else {
				logger.error("Nodes have more than one NEXT Arc");
				throw new IllegalArgumentException("Nodes have more than one NEXT Arc");
			}
		} else {
			return null;
		}
	}

	public static final boolean hasOutgoingArcOfType(INode current, String arcTypeName, IGraph graph) {
		IArcType arcType = graph.getArcType(arcTypeName);
		if (arcType != null) {
			return !current.getOutgoingArcsOfType(arcType).isEmpty();
		} else {
			return false;
		}
	}

	public static final List<INode> getNodesOfArcChain(IArc arc, IGraph graph) {
		List<INode> result = new ArrayList<INode>();
		INode current = arc.getTargetNode();
		result.add(current);
		if (hasOutgoingArcOfType(current, arc.getType().getName(), graph)) {
			List<? extends IArc> arcs = current.getOutgoingArcsOfType(arc.getType());
			for (IArc outArc : arcs) {
				result.addAll(getNodesOfArcChain(outArc, graph));
			}
		}
		return result;
	}

	public static final List<IArc> getArcsOfArcChain(IArc arc, IGraph graph) {
		List<IArc> result = new ArrayList<IArc>();
		result.add(arc);
		INode current = arc.getTargetNode();

		if (hasOutgoingArcOfType(current, arc.getType().getName(), graph)) {
			List<? extends IArc> arcs = current.getOutgoingArcsOfType(arc.getType());
			for (IArc outArc : arcs) {
				result.addAll(getArcsOfArcChain(outArc, graph));
			}
		}
		return result;
	}

	public static final List<String> getListFromArrayToString(String representation) {
		List<String> result = new ArrayList<>();
		if (representation != null && representation != "[]") {
			result = Arrays.asList(representation.substring(1, representation.length() - 1).split(", "));
		}
		return result;
	}

	public static final void deleteNodeAndReferences(INode node, IGraph graph) {
		List<? extends IArc> refs = node.getOutgoingArcsOfType(graph.getArcType("reference"));
		if (!refs.isEmpty()) {
			for (IArc iArc : refs) {
				List<IArc> refarcs = getArcsOfArcChain(iArc, graph);
				for (IArc iArc2 : refarcs) {
					graph.deleteArc(iArc2);
				}
			}
		}
		graph.deleteNode(node);
	}
}
