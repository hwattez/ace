package constraints.extension.structures;

import static org.xcsp.common.Constants.STAR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.xcsp.common.Constants;
import org.xcsp.common.Range;
import org.xcsp.common.Utilities;
import org.xcsp.common.structures.Automaton;
import org.xcsp.common.structures.Transition;

import constraints.Constraint;
import constraints.extension.CMDD;
import utility.Kit;
import utility.Kit.IntArrayHashKey;
import variables.Domain;
import variables.Variable;

/**
 * This is the class for the MDD form of extension structures. All supports (allowed tuples) are recorded as paths in the Multi-valued Decision Diagram. Note
 * that tuples are recorded with indexes (of values).
 * 
 * @author Christophe Lecoutre
 */
public final class MDD extends ExtensionStructure {

	/**********************************************************************************************
	 * Intern class MDDNode
	 *********************************************************************************************/

	/**
	 * The False terminal node of the MDD (with id 0)
	 */
	public final MDDNode nodeF = new MDDNode(-1);

	/**
	 * The True terminal node of the MDD (with id 1)
	 */
	public final MDDNode nodeT = new MDDNode(-1);

	private boolean discardClassForNodeF = true; // hard coding

	/**
	 * The class for representing a node in an MDD
	 */
	public final class MDDNode {

		/**
		 * The id of this node (must be unique)
		 */
		public int id;

		/**
		 * The level of this node in the MDD to which it belongs
		 */
		public final int level;

		/**
		 * The children (sons) of this node
		 */
		public MDDNode[] sons;

		/**
		 * Equivalence classes among sons of the node; sonsClasses[i][j] is the index of the jth son in the ith equivalence class. Two indexes belong to the
		 * same class iff they reach the same child.
		 */
		public int[][] sonsClasses;

		/**
		 * The number of sons that are different from the False terminal node. This is used as a cache.
		 */
		private Integer nSonsNotNodeF;

		/**
		 * Object used temporarily when building an MDD from an automaton or a KnapsSack; This can be an Integer or a String.
		 */
		private Object state;

		/**
		 * Returns true if this node is one of the two terminal nodes
		 * 
		 * @return true if this node is one of the two terminal nodes
		 */
		private final boolean isLeaf() {
			return this == nodeF || this == nodeT;
		}

		/**
		 * Returns the number of sons that are different from the False terminal node
		 * 
		 * @return the number of sons that are different from the False terminal node
		 */
		public int nSonsNotNodeF() {
			return nSonsNotNodeF != null ? nSonsNotNodeF : (nSonsNotNodeF = (int) Stream.of(sons).filter(c -> c != nodeF).count());
		}

		/**
		 * Returns the number of internal nodes (i.e., other than terminal ones) that can be reached from this node. Nodes whose id is in the specifies set must
		 * be ignored (because already counted)
		 * 
		 * @param set
		 *            the ids of nodes that must ignored
		 * @return the number of internal nodes that can be reached from this node
		 */
		private int nInternalNodes(Set<Integer> set) {
			if (isLeaf() || set.contains(id))
				return 0; // static leaves are not counted here and nodes with id already in set are already counted
			set.add(id);
			return 1 + Stream.of(sons).mapToInt(c -> c.nInternalNodes(set)).sum();
		}

		private MDDNode(int level) {
			this.id = nCreatedNodes++;
			this.level = level < 0 ? -1 : level; // -1 for the two special terminal nodes
		}

		private MDDNode(int level, int nSons, boolean defaultNodeF) {
			this(level);
			this.sons = IntStream.range(0, nSons).mapToObj(i -> defaultNodeF ? nodeF : nodeT).toArray(MDDNode[]::new);
		}

		private MDDNode(int level, int nSons, boolean defaultNodeF, Object state) {
			this(level, nSons, defaultNodeF);
			this.state = state;
		}

		private void addTuple(int level, int index, int[] tuple, boolean positive, int[] domSizes) {
			MDDNode son = sons[index];
			if (!son.isLeaf())
				son.addTuple(level + 1, tuple, positive, domSizes);
			else if (level == tuple.length - 1)
				sons[index] = positive ? nodeT : nodeF;
			else
				(sons[index] = new MDDNode(level + 1, domSizes[level + 1], positive)).addTuple(level + 1, tuple, positive, domSizes);
		}

		private void addTuple(int level, int[] tuple, boolean positive, int[] domSizes) {
			if (tuple[level] == Constants.STAR) {
				for (int i = 0; i < sons.length; i++)
					addTuple(level, i, tuple, positive, domSizes);
			} else
				addTuple(level, tuple[level], tuple, positive, domSizes);
		}

		/**
		 * Adds the specified tuple to the MDD
		 * 
		 * @param tuple
		 *            a tuple to be added to the MDD
		 * @param positive
		 *            indicates if the tuple is a support or a conflict
		 * @param domSizes
		 *            the size of the domains at each level
		 */
		public void addTuple(int[] tuple, boolean positive, int[] domSizes) {
			addTuple(0, tuple, positive, domSizes);
		}

		/**
		 * Builds the equivalence classes of the sons of the node
		 */
		private void buildSonsClasses() {
			if (isLeaf() || sonsClasses != null)
				return; // already built
			Map<MDDNode, List<Integer>> map = IntStream.range(0, sons.length).filter(i -> !discardClassForNodeF || sons[i] != nodeF).boxed()
					.collect(Collectors.groupingBy(i -> sons[i]));
			sonsClasses = map.values().stream().map(list -> Kit.intArray(list)).toArray(int[][]::new);
			Stream.of(sons).forEach(s -> s.buildSonsClasses());
		}

		private boolean canReachNodeT(Set<Integer> reachingNodes, Set<Integer> unreachingNodes) {
			if (this == nodeT || reachingNodes.contains(id))
				return true;
			if (this == nodeF || unreachingNodes.contains(id))
				return false;
			boolean found = false;
			for (int i = 0; i < sons.length; i++)
				if (!sons[i].canReachNodeT(reachingNodes, unreachingNodes))
					sons[i] = nodeF;
				else
					found = true;
			if (found)
				reachingNodes.add(id);
			else
				unreachingNodes.add(id);
			return found;
		}

		public boolean canReachNodeT() {
			return canReachNodeT(new HashSet<Integer>(), new HashSet<Integer>());
		}

		public int renameNodes(int lastId, Map<Integer, MDDNode> map) {
			if (isLeaf() || map.get(id) == this)
				return lastId;
			lastId++;
			map.put(id = lastId, this);
			for (MDDNode son : sons)
				lastId = son.renameNodes(lastId, map);
			// for (int i = 0; i < childClasses.length; i++) lastId = childs[childClasses[i][0]].renameNodes(lastId, map); // alternative
			return lastId;
		}

		private boolean controlUniqueNodes(Map<Integer, MDDNode> map) {
			MDDNode node = map.get(id);
			if (node == null)
				map.put(id, this);
			else
				Kit.control(node == this, () -> "two nodes with the same id in the MDD " + id);
			return sons == null || Stream.of(sons).noneMatch(child -> !child.controlUniqueNodes(map));
		}

		public void display(int[] cnts, boolean displayClasses) {
			if (this.isLeaf())
				return;
			Kit.log.fine(id + "@" + level + " => ");
			if (cnts != null)
				cnts[level]++;
			if (sons == null)
				return;
			Kit.log.fine("{" + Stream.of(sons).map(child -> child.id + "").collect(Collectors.joining(",")) + "}");
			if (displayClasses) {
				if (sonsClasses != null)
					for (int i = 0; i < sonsClasses.length; i++)
						Kit.log.fine("class " + i + " => {" + Kit.join(sonsClasses[i]) + "}");
				Kit.log.fine("nNotFFChilds=" + nSonsNotNodeF);
			}
			// if (similarChilds != null) for (int i = 0; i < similarChilds.length; i++)childs[similarChilds[i][0]].display(constraint, cnts);
			// else
			Stream.of(sons).filter(s -> s.id > id).forEach(s -> s.display(cnts, displayClasses));
		}

		public void display() {
			display(null, false);
		}

		public int displayTuples(Domain[] doms, int[] currTuple, int currLevel, int cnt) {
			if (this == nodeT) { // && Kit.isArrayContainingValuesAllDifferent(currentTuple)) {
				Kit.log.info(Kit.join(currTuple));
				cnt++;
			}
			if (isLeaf())
				return cnt;
			for (int i = 0; i < sons.length; i++) {
				currTuple[currLevel] = doms[currLevel].toVal(i);
				cnt = sons[i].displayTuples(doms, currTuple, currLevel + 1, cnt);
			}
			return cnt;
		}

		public void collectCompressedTuples(List<int[][]> list, int[][] t, int level) {
			if (this == nodeT)
				list.add(Kit.cloneDeeply(t));
			if (isLeaf())
				return;
			for (int i = 0; i < sonsClasses.length; i++) {
				t[level] = sonsClasses[i];
				MDDNode representativeChild = sons[sonsClasses[i][0]];
				representativeChild.collectCompressedTuples(list, t, level + 1);
			}
		}

		public MDDNode filter(int[][] values, int prevVal) {
			if (isLeaf())
				return this;
			// int left = -1;
			// for (int i = childs.length - 1; i >= 0; i--)
			// if (values[i] > prevVal && childs[i] != nodeF) {
			// left = i; break; }
			// MDDNode node = null;
			// if (left == -1) node = this;
			// else {
			MDDNode node = new MDDNode(level, sons.length, true);
			for (int i = 0; i < sons.length; i++)
				if (values[level][i] <= prevVal)
					node.sons[i] = sons[i];
			// }
			for (int i = 0; i < sons.length; i++)
				node.sons[i] = node.sons[i].filter(values, values[level][i]);
			return node;
		}
	}

	/**********************************************************************************************
	 * Class members
	 *********************************************************************************************/

	@Override
	public boolean checkIndexes(int[] t) {
		MDDNode node = root;
		for (int i = 0; !node.isLeaf(); i++)
			node = node.sons[t[i]];
		return node == nodeT;
	}

	/**
	 * The root node of the MDD
	 */
	public MDDNode root;

	/**
	 * The number of nodes in the MDD (used as a cache)
	 */
	private Integer nNodes;

	/**
	 * The number of nodes already created in the MDD; useful during construction
	 */
	private int nCreatedNodes = 0;

	private boolean reductionWhileProcessingTuples = false; // hard coding

	/**
	 * The number of nodes in the MDD
	 * 
	 * @return the number of nodes in the MDD
	 */
	public Integer nNodes() {
		return nNodes != null ? nNodes : (nNodes = 2 + root.nInternalNodes(new HashSet<Integer>()));
	}

	public MDD(CMDD c) {
		super(c);
	}

	public MDD(CMDD c, MDDNode root) {
		this(c);
		this.root = root;
	}

	public MDD(CMDD c, Automaton automata) {
		this(c);
		storeTuplesFromAutomata(automata, c.scp.length, Stream.of(c.scp).map(x -> x.dom).toArray(Domain[]::new));
	}

	public MDD(CMDD c, Transition[] transitions) {
		this(c);
		storeTuplesFromTransitions(transitions, Stream.of(c.scp).map(x -> x.dom).toArray(Domain[]::new));
	}

	public MDD(CMDD c, int[] coeffs, Object limits) {
		this(c);
		storeTuplesFromKnapsack(coeffs, limits, Variable.initDomainValues(c.scp));
	}

	private MDDNode recursiveReduction(MDDNode node, Map<IntArrayHashKey, MDDNode> reductionMap) {
		if (node.isLeaf())
			return node;
		for (int i = 0; i < node.sons.length; i++)
			node.sons[i] = recursiveReduction(node.sons[i], reductionMap);
		IntArrayHashKey hk = new IntArrayHashKey(Stream.of(node.sons).mapToInt(c -> c.id).toArray());
		return reductionMap.computeIfAbsent(hk, k -> node);
	}

	private void reduce(int[] prevTuple, int[] currTuple, Map<IntArrayHashKey, MDDNode> reductionMap) {
		int i = 0;
		MDDNode node = root;
		while (prevTuple[i] == currTuple[i])
			node = node.sons[prevTuple[i++]];
		// assuming that tuples come in lex ordering, we can definitively reduce the left branch
		node.sons[prevTuple[i]] = recursiveReduction(node.sons[prevTuple[i]], reductionMap);
	}

	private void finalizeStoreTuples() {
		root.buildSonsClasses();
		nNodes = root.renameNodes(1, new HashMap<Integer, MDDNode>()) + 1;
		// System.out.println("MDD : nNodes=" + nNodes + " nBuiltNodes=" + nCreatedNodes);
		assert root.controlUniqueNodes(new HashMap<Integer, MDDNode>());
		// buildSplitter();
		// root.display();
	}

	@Override
	public void storeTuples(int[][] tuples, boolean positive) {
		Kit.control(positive && tuples.length > 0);
		Constraint c = firstRegisteredCtr();
		int[] domainSizes = Variable.domSizeArrayOf(c.scp, true);
		Map<IntArrayHashKey, MDDNode> reductionMap = new HashMap<>(2000);
		this.root = new MDDNode(0, domainSizes[0], positive);
		if (c.indexesMatchValues) {
			for (int i = 0; i < tuples.length; i++) {
				root.addTuple(tuples[i], positive, domainSizes);
				if (reductionWhileProcessingTuples && i > 0)
					reduce(tuples[i - 1], tuples[i], reductionMap);
			}
		} else {
			// we need to pass from tuples of values in tuples of indexes (of values)
			int[] previousTuple = null, currentTuple = new int[tuples[0].length];
			for (int[] tuple : tuples) {
				for (int i = 0; i < currentTuple.length; i++)
					currentTuple[i] = tuple[i] == STAR ? STAR : c.scp[i].dom.toIdx(tuple[i]);
				root.addTuple(currentTuple, positive, domainSizes);
				if (reductionWhileProcessingTuples) {
					if (previousTuple == null)
						previousTuple = currentTuple.clone();
					else {
						reduce(previousTuple, currentTuple, reductionMap);
						int[] tmp = previousTuple;
						previousTuple = currentTuple;
						currentTuple = tmp;
					}
				}
			}
			// constraint.setIndexValueSimilarity(true);
		}
		if (!reductionWhileProcessingTuples)
			recursiveReduction(root, reductionMap);
		finalizeStoreTuples();
	}

	public MDD storeTuplesFromTransitions(Transition[] transitions, Domain[] domains) {
		Map<String, MDDNode> nodes = new HashMap<>();
		Set<String> possibleRoots = new HashSet<>(), notRoots = new HashSet<>();
		Set<String> possibleWells = new HashSet<>(), notWells = new HashSet<>();
		for (Transition tr : transitions) {
			String src = tr.start, tgt = tr.end;
			notWells.add(src);
			notRoots.add(tgt);
			if (!notRoots.contains(src))
				possibleRoots.add(src);
			if (!notWells.contains(tgt))
				possibleWells.add(tgt);
			if (possibleRoots.contains(tgt))
				possibleRoots.remove(tgt);
			if (possibleWells.contains(src))
				possibleWells.remove(src);
		}
		Kit.control(possibleRoots.size() == 1 && possibleWells.size() == 1,
				() -> "sizes= " + possibleRoots.size() + " " + possibleWells.stream().collect(Collectors.joining(" ")));
		String sroot = possibleRoots.toArray(new String[1])[0];
		String swell = possibleWells.toArray(new String[1])[0];
		nodes.put(sroot, root = new MDDNode(0, domains[0].initSize(), true));
		nodes.put(swell, nodeT);
		// TODO reordering transitions to guarantee that the src node has already been generated
		for (Transition tr : transitions) {
			MDDNode node1 = nodes.get(tr.start);
			long v = tr.value instanceof Integer ? (Integer) tr.value : (Long) tr.value;
			int val = Utilities.safeInt(v);
			int idx = domains[node1.level].toIdx(val);
			Kit.control(idx != -1);
			MDDNode node2 = nodes.computeIfAbsent((String) tr.end, k -> new MDDNode(node1.level + 1, domains[node1.level + 1].initSize(), true));
			// MDDNode node2 = nodes.get(tr[2]);
			// if (node2 == null)
			// nodes.put((String) tr[2], node2 = new MDDNode(this, node1.level + 1, domains[node1.level + 1].initSize(), true));
			node1.sons[idx] = node2;
		}
		// root.canReachNodeT();
		finalizeStoreTuples();
		return this;
	}

	private Map<String, List<Transition>> buildNextTransitions(Automaton automata) {
		Map<String, List<Transition>> map = new HashMap<>();
		map.put(automata.startState, new ArrayList<>());
		Stream.of(automata.finalStates).forEach(s -> map.put(s, new ArrayList<>()));
		Stream.of(automata.transitions).forEach(t -> {
			map.put(t.start, new ArrayList<>());
			map.put(t.end, new ArrayList<>());
		});
		Stream.of(automata.transitions).forEach(t -> map.get(t.start).add(t));
		return map;
	}

	public MDD storeTuplesFromAutomata(Automaton automata, int arity, Domain[] domains) {
		Kit.control(arity > 1 && IntStream.range(1, domains.length).allMatch(i -> domains[i].typeIdentifier() == domains[0].typeIdentifier()));
		Map<String, List<Transition>> nextTrs = buildNextTransitions(automata);
		this.root = new MDDNode(0, domains[0].initSize(), true, automata.startState);
		Map<String, MDDNode> prevs = new HashMap<>(), nexts = new HashMap<>();
		prevs.put((String) root.state, root);
		for (int i = 0; i < arity; i++) {
			for (MDDNode node : prevs.values()) {
				for (Transition tr : nextTrs.get(node.state)) {
					int v = Utilities.safeInt((Long) tr.value);
					int a = domains[i].toIdx(v);
					if (a != -1) {
						String nextState = tr.end;
						if (i == arity - 1) {
							node.sons[a] = Utilities.indexOf(nextState, automata.finalStates) != -1 ? nodeT : nodeF;
						} else {
							// MDDNode nextNode = nexts.computeIfAbsent(nextState, k -> new MDDNode(this, i + 1, domains[i].initSize(), true,
							// nextState)); // pb with i not final
							MDDNode nextNode = nexts.get(nextState);
							if (nextNode == null)
								nexts.put(nextState, nextNode = new MDDNode(i + 1, domains[i].initSize(), true, nextState));
							node.sons[a] = nextNode;
						}
					}
				}
			}
			Map<String, MDDNode> tmp = prevs;
			prevs = nexts;
			nexts = tmp;
			nexts.clear();
		}
		root.canReachNodeT();
		finalizeStoreTuples();
		return this;
	}

	// coeffs, and limits (either a Range or an int array) from the knapsack constraint, and values are the possible values at each level
	public MDD storeTuplesFromKnapsack(int[] coeffs, Object limits, int[][] values) {
		this.root = new MDDNode(0, values[0].length, true, 0);
		Map<Integer, MDDNode> prevs = new HashMap<>(), nexts = new HashMap<>();
		prevs.put((Integer) root.state, root);
		for (int level = 0; level < coeffs.length; level++) {
			for (MDDNode node : prevs.values()) {
				for (int j = 0; j < values[level].length; j++) {
					int nextState = (Integer) node.state + coeffs[level] * values[level][j];
					if (level == coeffs.length - 1) {
						if (limits instanceof Range)
							node.sons[j] = ((Range) limits).contains(nextState) ? nodeT : nodeF;
						else
							node.sons[j] = Utilities.indexOf(nextState, (int[]) limits) != -1 ? nodeT : nodeF;
					} else {
						MDDNode nextNode = nexts.get(nextState);
						if (nextNode == null)
							nexts.put(nextState, nextNode = new MDDNode(level + 1, values[level + 1].length, true, nextState));
						node.sons[j] = nextNode;
					}
				}
			}
			Map<Integer, MDDNode> tmp = prevs;
			prevs = nexts;
			nexts = tmp;
			nexts.clear();
		}
		root.canReachNodeT();

		boolean increasing = false;
		if (!increasing) {
			finalizeStoreTuples();
			// displayTuples();
		} else {
			root = root.filter(values, Integer.MAX_VALUE);
			recursiveReduction(root, new HashMap<>(2000));
			finalizeStoreTuples();
			root.display();
			displayTuples();
		}
		return this;
	}

	public void displayTuples() {
		int cnt = root.displayTuples(Variable.buildDomainsArrayFor(firstRegisteredCtr().scp), new int[firstRegisteredCtr().scp.length], 0, 0);
		Kit.log.info(" => " + cnt + " tuples");
	}

	/**********************************************************************************************
	 * Start of experimental section (splitting - compression)
	 *********************************************************************************************/

	private final class MDDSplitter {

		private final int[] splitMode;

		private final int[][][] splitTuples;

		private Set<int[]>[] splitSets; // used during collecting tuples

		private Map<Integer, Integer>[] auxiliaryLevelMaps;

		private MDDSplitter(int[] initialSplitMode) {
			this.splitMode = initialSplitMode;
			// assert Kit.sum(initialSplitMode) == mdd.firstRegisteredCtr().scp.length;
			for (int i = 0; i < splitMode.length; i++)
				if (i == 0 || i == splitMode.length - 1)
					splitMode[i] += 1; // because one additional variable
				else
					splitMode[i] += 2; // because two additional variables
			this.splitSets = IntStream.range(0, initialSplitMode.length).mapToObj(i -> new TreeSet<>(Utilities.lexComparatorInt)).toArray(Set[]::new);
			this.auxiliaryLevelMaps = IntStream.range(0, initialSplitMode.length - 1).mapToObj(i -> new HashMap<>()).toArray(Map[]::new);

			split2(root, 0);
			this.splitTuples = new int[splitSets.length][][];
			for (int i = 0; i < splitTuples.length; i++) {
				splitTuples[i] = splitSets[i].toArray(new int[splitSets[i].size()][]);
				splitSets[i].clear();
				splitSets[i] = null;
			}
			for (int i = 0; i < splitTuples.length; i++) {
				System.out.println("i=" + i + " size=" + splitTuples[i].length);
				// for (int[] t : splitTuples[i])
				// System.out.println(Toolkit.buildStringFromInts(t));
			}
		}

		private int getAuxiliaryLevelNodeId(int nodeId, int splitLevel) {
			return auxiliaryLevelMaps[splitLevel].computeIfAbsent(nodeId, k -> auxiliaryLevelMaps[splitLevel].size());
		}

		private void getTuples(MDDNode node, int splitLevel, int[] currentTuple, int currentLevel, int stoppingLevel) {
			if (node == nodeF)
				return;
			if (stoppingLevel == -1) {
				if (node == nodeT)
					splitSets[splitLevel].add(currentTuple.clone());
				else
					for (int i = 0; i < node.sons.length; i++) {
						currentTuple[currentLevel] = i;
						getTuples(node.sons[i], splitLevel, currentTuple, currentLevel + 1, stoppingLevel);
					}
			} else {
				assert node != nodeT && stoppingLevel != -1;
				if (currentLevel == stoppingLevel) {
					currentTuple[currentLevel] = getAuxiliaryLevelNodeId(node.id, splitLevel);
					splitSets[splitLevel].add(currentTuple.clone());

					// System.out.println("splitLevel = " + splitLevel + "currentLevel=" + currentLevel);
					split2(node, splitLevel + 1);
				} else
					for (int i = 0; i < node.sons.length; i++) {
						currentTuple[currentLevel] = i;
						getTuples(node.sons[i], splitLevel, currentTuple, currentLevel + 1, stoppingLevel);
					}
			}
		}

		public void split2(MDDNode startingNode, int splitLevel) {
			int[] currentTuple = new int[splitMode[splitLevel]];
			int currentLevel = 0;
			if (splitLevel > 0)
				currentTuple[currentLevel++] = getAuxiliaryLevelNodeId(startingNode.id, splitLevel - 1);
			getTuples(startingNode, splitLevel, currentTuple, currentLevel, (splitLevel == splitSets.length - 1 ? -1 : splitMode[splitLevel] - 1));
		}

	}

	private boolean mustBuildSplitter = false;

	public MDDSplitter splitter;

	void buildSplitter() {
		if (mustBuildSplitter) {
			int arity = firstRegisteredCtr().scp.length;
			splitter = new MDDSplitter(new int[] { (int) Math.ceil(arity / 2.0), (int) Math.floor(arity / 2.0) });
		}
	}

	public int[][][] buildCompressedTable() {
		// root.buildChildClasses();
		Constraint ctr = firstRegisteredCtr();
		LinkedList<int[][]> list = new LinkedList<>();
		root.collectCompressedTuples(list, new int[ctr.scp.length][], 0);
		int[][][] compressedTuples = Kit.intArray3D(list);
		return compressedTuples;
	}

	@SuppressWarnings("unused")
	public static void main(String[] args) {
		int length = Integer.parseInt(args[0]);
		// buildFromAutomata(2, new int[][] { { 1, 2 }, { -1, 3 }, { 3, -1 }, { 3, 4 }, { -1, -1 } }, 0, new int[] { 4 }, 5);
		// Automata automata = new Automata(10, 2, new int[][] { { 0, 0, 1 }, { 0, 1, 2 }, { 1, 1, 3 }, { 2, 0, 3 }, { 3, 0, 3 }, { 3, 1, 4
		// } }, 0, new int[] {
		// 4 });
		// Automata automata = null;
		// automata = new Automata(10, 3, new int[][] { { 0, 0, 1 }, { 0, 2, 2 }, { 1, 0, 3 }, { 2, 2, 4 }, { 3, 0, 5 }, { 3, 1, 7 }, { 4,
		// 1, 7 }, { 4, 2, 6 },
		// { 5, 1, 7 }, { 6, 1, 7 },
		// { 7, 1, 8 }, { 8, 0, 1 }, { 8, 1, 9 }, { 8, 2, 2 }, { 9, 0, 1 }, { 9, 2, 2 } }, 0, new int[] { 3, 4, 5, 6, 8, 9 });
		// automata = new Automata(7, 4, new int[][] { { 0, 0, 0 }, { 0, 1, 1 }, { 0, 2, 2 }, { 0, 3, 3 }, { 1, 0, 4 }, { 1, 1, 1 }, { 2, 0,
		// 5 }, { 2, 2, 2 }, {
		// 3, 0, 6 }, { 3, 3, 3 },
		// { 4, 0, 4 }, { 4, 1, 1 }, {4,2,2}, { 5, 0, 5 }, { 5, 2, 2 }, {5,3,3}, { 6, 0, 6 }, { 6, 3, 3 } ,{6,1,1}}, 0, new int[] { 0, 1, 2,
		// 3, 4, 5,6 });

		// automata = new Automata(5, 2, new int[][] { { 0, 0, 0 }, { 0, 1, 1 }, { 1, 1, 2 }, { 2, 0, 3 }, { 3, 0, 3 }, { 3, 1, 4 }, { 4, 0,
		// 4 } }, 0, new int[]
		// { 4 });
		// MDD m = new MDD(null).storeTuples(automata, length);

		// MDD m = new MDD(null).storeTuplesFromKnapsack(70,82,new int[] {27,37,45,53} , new int[] {0,1,2,3});
		// MDD m = new MDD(null).storeTuplesFromKnapsack(34,34, Kit.buildIntArrayWithUniqueValue(4, 1) , Kit.buildIntArray(16, 1));
		// MDD m = new MDD(null).storeTuplesFromKnapsack(65,65, Kit.buildIntArrayWithUniqueValue(5, 1) , Kit.buildIntArray(25, 1));
		// MDD m = new MDD(null).storeTuplesFromKnapsack(111,111, Kit.buildIntArrayWithUniqueValue(6, 1) , Kit.buildIntArray(36, 1));
		// MDD m = new MDD(null).storeTuplesFromKnapsack(175,175, Kit.buildIntArrayWithUniqueValue(7, 1) , Kit.buildIntArray(49, 1));
		// MDD m = new MDD(null).storeTuplesFromKnapsack(505,505, Kit.buildIntArrayWithUniqueValue(10, 1) , Kit.buildIntArray(100, 1));
		// MDD m = new MDD(null).storeTuplesFromKnapsack(1379,1379, Kit.buildIntArrayWithUniqueValue(14, 1) , Kit.buildIntArray(196, 1));
		// MDD m = new MDD(null).storeTuplesFromKnapsack(Kit.repeat(1, 20), new Range(4010, 4010), Kit.range(1, 400));
		// m.root.display();
		// Kit.prn(" => " + m.root.displayTuples(new int[length], 0, 0) + " tuples");
	}
}

// if (!directEncodingFromAutomata) buildSubtree root.buildSubtree(nbLetters, transitions, finalStates, height + 1, 0, new
// HashMap<IntArrayHashKey,
// MDDNode>(2000));

// public static void storeTuples1(int nbStates, int nbLetters, int[][] transitions, int initialState, int[] finalStates, int nbLevels) {
// Kit.control(nbLevels > 1);
// Map<Integer, MDDNode> map = new HashMap<Integer, MDDNode>();
// MDDNode root = new MDDNode(this,0, false, nbLetters); // TODO a virer la declaertaionxxxxxxxxxxxxxxxxxxxxxxxxxxx
// List<MDDNode> listOfNodesAtCurrentLevel = new ArrayList<MDDNode>(), nextList = new ArrayList<MDDNode>();
// listOfNodesAtCurrentLevel.add(root);
// root.state = initialState;
// for (int level = 0; level < nbLevels - 1; level++) {
// nextList.clear();
// for (MDDNode node : listOfNodesAtCurrentLevel) {
// map.clear();
// for (int letter = 0; letter < nbLetters; letter++) {
// int nextState = transitions[node.state][letter];
// if (nextState == -1)
// continue;
// if (level == nbLevels - 2) {
// if (Kit.isArrayContaining(finalStates, nextState)) {
// node.setChild(letter, MDDNode.nodeTT);
// }
// continue;
// }
// MDDNode nextNode = map.get(nextState);
// if (nextNode == null) {
// nextNode = new MDDNode(this,level + 1, true, nbLetters);
// nextNode.state = nextState;
// map.put(nextState, nextNode);
// nextList.add(nextNode);
// }
// node.setChild(letter, nextNode);
// }
// }
// List<MDDNode> tmp = listOfNodesAtCurrentLevel;
// listOfNodesAtCurrentLevel = nextList;
// nextList = tmp;
// }
// root.display(null);
// }

// private static boolean buildSubtree(MDDNode node, int nbLetters, int[][] transitions, int[] finalStates, int nbLevels, int level) {
// boolean found = false;
// Map<Integer, MDDNode> map = new HashMap<Integer, MDDNode>();
// for (int letter = 0; letter < nbLetters; letter++) {
// int nextState = transitions[node.state][letter];
//
// boolean trueNode = false;
// if (level == nbLevels - 2) {
// trueNode = nextState != -1 && Kit.isArrayContaining(finalStates, nextState);
// node.setChild(letter, trueNode ? MDDNode.nodeTT : MDDNode.nodeFF);
// } else {
// MDDNode nextNode = map.get(nextState);
// if (nextNode == null) {
// nextNode = new MDDNode(this,level + 1, true, nbLetters);
// nextNode.state = nextState;
// map.put(nextState, nextNode);
// }
// trueNode = nextState != -1 && buildSubtree(nextNode, nbLetters, transitions, finalStates, nbLevels, level + 1);
// node.setChild(letter, trueNode ? nextNode : MDDNode.nodeFF);
// }
//
// found = found || trueNode;
// }
//
//
// /*
// * MDDNode[] childs = node.getChilds(); int[] t = new int[childs.length]; for (int i = 0; i < childs.length; i++) t[i] =
// childs[i].getId(); IntArrayHashKey hk
// = new IntArrayHashKey(t); MDDNode
// identicalNode =
// * reductionMapBisTmp.get(hk); if (identicalNode == null) { reductionMapBisTmp.put(hk, node); return node; } else return identicalNode;
// */
//
// return found;
// }
