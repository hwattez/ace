package problem;

import static org.xcsp.common.Utilities.join;
import static org.xcsp.parser.callbacks.XCallbacks.XCallbacksParameters.RECOGNIZE_BINARY_PRIMITIVES;
import static org.xcsp.parser.callbacks.XCallbacks.XCallbacksParameters.RECOGNIZE_EXTREMUM_CASES;
import static org.xcsp.parser.callbacks.XCallbacks.XCallbacksParameters.RECOGNIZE_LOGIC_CASES;
import static org.xcsp.parser.callbacks.XCallbacks.XCallbacksParameters.RECOGNIZE_SUM_CASES;
import static org.xcsp.parser.callbacks.XCallbacks.XCallbacksParameters.RECOGNIZE_TERNARY_PRIMITIVES;
import static org.xcsp.parser.callbacks.XCallbacks.XCallbacksParameters.RECOGNIZE_UNARY_PRIMITIVES;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.xcsp.common.Condition;
import org.xcsp.common.Condition.ConditionVar;
import org.xcsp.common.IVar;
import org.xcsp.common.Types.TypeCtr;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.Types.TypeFlag;
import org.xcsp.common.Types.TypeFramework;
import org.xcsp.common.Types.TypeObjective;
import org.xcsp.common.Types.TypeOperatorRel;
import org.xcsp.common.Types.TypeRank;
import org.xcsp.common.Types.TypeVar;
import org.xcsp.common.domains.Domains.Dom;
import org.xcsp.common.domains.Domains.DomSymbolic;
import org.xcsp.common.predicates.MatcherInterface;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.common.structures.AbstractTuple;
import org.xcsp.common.structures.Automaton;
import org.xcsp.common.structures.Transition;
import org.xcsp.modeler.api.ProblemAPI;
import org.xcsp.modeler.entities.CtrEntities.CtrArray;
import org.xcsp.modeler.entities.CtrEntities.CtrEntity;
import org.xcsp.modeler.entities.ModelingEntity;
import org.xcsp.parser.XParser;
import org.xcsp.parser.callbacks.XCallbacks2;
import org.xcsp.parser.entries.ParsingEntry;
import org.xcsp.parser.entries.ParsingEntry.CEntry;
import org.xcsp.parser.entries.XConstraints.XBlock;
import org.xcsp.parser.entries.XConstraints.XCtr;
import org.xcsp.parser.entries.XConstraints.XGroup;
import org.xcsp.parser.entries.XConstraints.XLogic;
import org.xcsp.parser.entries.XConstraints.XSlide;
import org.xcsp.parser.entries.XObjectives.XObj;
import org.xcsp.parser.entries.XVariables.XArray;
import org.xcsp.parser.entries.XVariables.XVar;
import org.xcsp.parser.entries.XVariables.XVarInteger;
import org.xcsp.parser.entries.XVariables.XVarSymbolic;

import constraints.Constraint.CtrFalse;
import dashboard.Control.SettingXml;
import dashboard.Input;
import variables.Variable;
import variables.Variable.VariableInteger;
import variables.Variable.VariableSymbolic;;

/**
 * This class corresponds to a problem loading instances in XCSP3 format.
 */
public class XCSP3 implements ProblemAPI, XCallbacks2 {

	private Implem implem = new Implem(this); // implementation for callbacks

	@Override
	public Implem implem() {
		return implem;
	}

	private Problem problem; // set at the beginning of data() because the api and the imp objects have not been linked at time of field construction

	private List<String> filenames;

	@Override
	public String name() {
		return filenames.get(problem.head.instanceNumber);
	}

	private List<String> collect(List<String> list, File f) {
		if (f.isDirectory())
			Stream.of(f.listFiles()).forEach(g -> collect(list, g));
		else if (Stream.of(".xml", ".lzma").anyMatch(suf -> f.getName().endsWith(suf)))
			list.add(f.getAbsolutePath());
		return list;
	}

	public void data() { // called automatically by reflection
		this.problem = (Problem) imp();
		String s = problem.askString("File or directory:");
		if (filenames == null) {
			filenames = collect(new ArrayList<>(), new File(s)).stream().sorted().collect(Collectors.toList());
			Input.nInstancesToSolve = filenames.size();
		}
		problem.parameters.get(0).setValue(name());
		System.out.println();
	}

	@Override
	public void model() {
		SettingXml settings = problem.head.control.xml;
		implem.currParameters.remove(RECOGNIZE_UNARY_PRIMITIVES);
		implem.currParameters.remove(RECOGNIZE_BINARY_PRIMITIVES);
		implem.currParameters.remove(RECOGNIZE_TERNARY_PRIMITIVES);
		implem.currParameters.remove(RECOGNIZE_LOGIC_CASES);
		implem.currParameters.remove(RECOGNIZE_EXTREMUM_CASES);
		implem.currParameters.remove(RECOGNIZE_SUM_CASES);
		try {
			if (problem.head.control.general.verbose > 1)
				XParser.VERBOSE = true;
			if (settings.discardedClasses.indexOf(',') < 0)
				loadInstance(name(), settings.discardedClasses);
			else
				loadInstance(name(), settings.discardedClasses.split(","));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Problem when parsing the instance. Fix the problem.");
			System.exit(1);
		}
	}

	@Override
	public void beginInstance(TypeFramework type) {
		if (type == TypeFramework.COP)
			problem.head.control.toCOP();
	}

	private void copyBasicAttributes(ModelingEntity entity, ParsingEntry entry) {
		if (entry.id != null)
			entity.id(entry.id);
		if (entry.note != null)
			entity.note(entry.note);
		if (entry.classes != null)
			entity.tag(entry.classes);
	}

	/**********************************************************************************************
	 * Methods for transforming (mapping) parser objects into solver objects ; tr = tr(ansform)
	 *********************************************************************************************/

	private Map<XVar, Variable> mapVar = new LinkedHashMap<>();

	private VariableInteger trVar(IVar x) {
		return (VariableInteger) mapVar.get(x);
	}

	private VariableInteger trVar(XVarInteger x) {
		return (VariableInteger) mapVar.get(x);
	}

	private VariableSymbolic trVar(XVarSymbolic x) {
		return (VariableSymbolic) mapVar.get(x);
	}

	private Condition trVar(Condition condition) {
		return condition instanceof ConditionVar ? new ConditionVar(((ConditionVar) condition).operator, trVar(((ConditionVar) condition).x)) : condition;
	}

	private XNode<IVar> trVar(XNode<XVarInteger> tree) {
		return ((XNode) tree).replaceLeafValues(v -> v instanceof XVarInteger ? trVar((XVarInteger) v) : v);
	}

	private XNode<IVar> trVarSymbolic(XNode<XVarSymbolic> tree) {
		return ((XNode) tree).replaceLeafValues(v -> v instanceof XVarSymbolic ? trVar((XVarSymbolic) v) : v);
	}

	private XNode<IVar>[] trVar(XNode<XVarInteger>[] trees) {
		return Stream.of(trees).map(t -> t.replaceLeafValues(v -> v instanceof XVarInteger ? trVar((XVarInteger) v) : v)).toArray(XNode[]::new);
	}

	private VariableInteger[] trVars(XVarInteger[] t) {
		return Arrays.stream(t).map(v -> mapVar.get(v)).toArray(VariableInteger[]::new);
	}

	private VariableSymbolic[] trVars(XVarSymbolic[] t) {
		return Arrays.stream(t).map(v -> mapVar.get(v)).toArray(VariableSymbolic[]::new);
	}

	private VariableInteger[][] trVars2D(XVarInteger[][] m) {
		return Arrays.stream(m).map(t -> trVars(t)).toArray(VariableInteger[][]::new);
	}

	/**********************************************************************************************
	 * Methods for loading variables, constraints and objectives
	 *********************************************************************************************/

	@Override
	public void loadVar(XVar v) {
		implem.manageIdFor(v);
		if (v.degree > 0) {
			if (v.dom instanceof Dom)
				mapVar.put(v, (VariableInteger) var(v.id, (Dom) v.dom, v.note, v.classes));
			else if (v.dom instanceof DomSymbolic)
				mapVar.put(v, (VariableSymbolic) var(v.id, (DomSymbolic) v.dom, v.note, v.classes));
			else
				unimplementedCase(v);
		}
	}

	// method for mapping variables inside arrays
	private void completeMapVar(XArray va, Object a, int... indexes) {
		if (a != null)
			if (a.getClass().isArray())
				IntStream.range(0, Array.getLength(a)).forEach(i -> completeMapVar(va, Array.get(a, i), vals(indexes, i)));
			else
				mapVar.put(va.varAt(indexes), (Variable) a);
	}

	@Override
	public void loadArray(XArray va) {
		implem.manageIdFor(va);
		Object a = null;
		int[] sz = va.size;
		if (va.getType() == TypeVar.integer) {
			if (sz.length == 1)
				a = array(va.id, size(sz[0]), i -> va.domAt(i), va.note, va.classes);
			else if (sz.length == 2)
				a = array(va.id, size(sz[0], sz[1]), (i, j) -> va.domAt(i, j), va.note, va.classes);
			else if (sz.length == 3)
				a = array(va.id, size(sz[0], sz[1], sz[2]), (i, j, k) -> va.domAt(i, j, k), va.note, va.classes);
			else if (sz.length == 4)
				a = array(va.id, size(sz[0], sz[1], sz[2], sz[3]), (i, j, k, l) -> va.domAt(i, j, k, l), va.note, va.classes);
			else if (sz.length == 5)
				a = array(va.id, size(sz[0], sz[1], sz[2], sz[3], sz[4]), (i, j, k, l, m) -> va.domAt(i, j, k, l, m), va.note, va.classes);
			else
				unimplementedCase(va);
		} else if (va.getType() == TypeVar.symbolic) {
			if (sz.length == 1)
				a = arraySymbolic(va.id, size(sz[0]), i -> va.varAt(i) != null && va.varAt(i).degree > 0 ? (DomSymbolic) va.varAt(i).dom : null, va.note,
						va.classes);
			else
				unimplementedCase(va);
		} else
			unimplementedCase(va);
		completeMapVar(va, a);
	}

	@Override
	public void buildVarInteger(XVarInteger x, int minValue, int maxValue) {
		throw new AssertionError();
	}

	@Override
	public void buildVarInteger(XVarInteger x, int[] values) {
		throw new AssertionError();
	}

	@Override
	public void buildVarSymbolic(XVarSymbolic x, String[] values) {
		throw new AssertionError();
	}

	/**********************************************************************************************
	 * Methods for loading constraints
	 *********************************************************************************************/

	@Override
	public void loadBlock(XBlock block) {
		CtrEntity entity = block(() -> loadConstraints(block.subentries)); // recursive call
		copyBasicAttributes(entity, block);
	}

	@Override
	public void loadGroup(XGroup group) {
		CtrEntity entity = problem.manageLoop(() -> {
			if (group.template instanceof XCtr)
				loadCtrs((XCtr) group.template, group.argss, group);
			else if (group.template instanceof XLogic && ((XLogic) group.template).getType() == TypeCtr.not) {
				CEntry child = ((XLogic) group.template).components[0];
				if (child instanceof XCtr && ((XCtr) child).type == TypeCtr.allEqual)
					Stream.of(group.argss).forEach(o -> notAllEqual(trVars((XVarInteger[]) o)));
				else
					unimplementedCase(group);
			} else
				unimplementedCase(group);
		});
		if (entity != null)
			copyBasicAttributes(entity, group);
	}

	@Override
	public void loadSlide(XSlide s) {
		CtrArray entity = problem.manageLoop(() -> XCallbacks2.super.loadSlide(s));
		copyBasicAttributes(entity, s);
	}

	@Override
	public void beginLogic(XLogic l) {
		System.out.println("Begin : " + l);
	}

	@Override
	public void endLogic(XLogic l) {
		System.out.println("End : " + l);
	}

	@Override
	public void loadCtr(XCtr c) {
		if (problem.features.mustDiscard(c.vars()))
			return;
		if (problem.head.control.constraints.ignoredCtrType == c.type) {
			problem.features.nDiscardedCtrs++;
			return;
		}
		if (problem.head.control.constraints.ignoreCtrArity == c.vars().length) {
			problem.features.nDiscardedCtrs++;
			return;
		}
		int sizeBefore = problem.ctrEntities.allEntities.size();

		XCallbacks2.super.loadCtr(c);
		if (sizeBefore == problem.ctrEntities.allEntities.size())
			return; // must have been a true constraint (should be checked)
		CtrEntity entity = problem.ctrEntities.allEntities.get(problem.ctrEntities.allEntities.size() - 1);
		copyBasicAttributes(entity, c);
	}

	@Override
	public void buildCtrFalse(String id, XVar[] list) {
		if (problem.settings.framework == TypeFramework.MAXCSP)
			problem.addCtr(new CtrFalse(problem, trVars(Stream.of(list).map(x -> (XVarInteger) x).toArray(XVarInteger[]::new)), "CtrHard False for MaxCSP"));
		// extension((VarInteger[]) trVars(Stream.of(list).map(x -> (XVarInteger) x).toArray(XVarInteger[]::new)), new int[][] { {} });
		else
			throw new RuntimeException("Constraint with only conflicts");
	}

	// ************************************************************************
	// ***** Constraint intension
	// ************************************************************************

	@Override
	public void buildCtrIntension(String id, XVarInteger[] scope, XNodeParent<XVarInteger> tree) {
		control(tree.exactlyVars(scope), "Pb with scope");
		problem.intension((XNodeParent<IVar>) trVar(tree));
	}

	// ************************************************************************
	// ***** Constraint extension
	// ************************************************************************

	@Override
	public void buildCtrExtension(String id, XVarInteger x, int[] values, boolean positive, Set<TypeFlag> flags) {
		control(!flags.contains(TypeFlag.STARRED_TUPLES));
		values = flags.contains(TypeFlag.UNCLEAN_TUPLES) ? Variable.filterValues(trVar(x), values, false) : values;
		problem.extension(vars(trVar(x)), dub(values), positive);
	}

	@Override
	public void buildCtrExtension(String id, XVarInteger[] list, int[][] tuples, boolean positive, Set<TypeFlag> flags) {
		tuples = flags.contains(TypeFlag.UNCLEAN_TUPLES) ? Variable.filterTuples(trVars(list), tuples, false) : tuples;
		problem.extension(trVars(list), tuples, positive, flags.contains(TypeFlag.STARRED_TUPLES));
	}

	@Override
	public void buildCtrExtension(String id, XVarInteger[] list, AbstractTuple[] tuples, boolean positive, Set<TypeFlag> flags) {
		problem.extension(trVars(list), tuples, positive);
	}

	// ************************************************************************
	// ***** Constraints Regular and Mdd
	// ************************************************************************

	@Override
	public void buildCtrRegular(String id, XVarInteger[] list, Transition[] transitions, String startState, String[] finalStates) {
		// Transition[] ts = Stream.of(transitions).map(t -> new Transition((String) t[0], t[1], (String) t[2])).toArray(Transition[]::new);
		problem.regular(trVars(list), new Automaton(startState, transitions, finalStates));
	}

	@Override
	public void buildCtrMDD(String id, XVarInteger[] list, Transition[] transitions) {
		// System.out.println(transitions.getClass().getName());
		// Transition[] ts = Stream.of(transitions).map(t -> new Transition((String) t[0], t[1], (String) t[2])).toArray(Transition[]::new);
		problem.mdd(trVars(list), transitions);
	}

	// ************************************************************************
	// ***** Constraints AllDifferent and AllEqual
	// ************************************************************************

	@Override
	public void buildCtrAllDifferent(String id, XVarInteger[] list) {
		problem.allDifferent(trVars(list));
	}

	@Override
	public void buildCtrAllDifferentExcept(String id, XVarInteger[] list, int[] except) {
		problem.allDifferent(trVars(list), except);
	}

	@Override
	public void buildCtrAllDifferentList(String id, XVarInteger[][] lists) {
		problem.allDifferentList(trVars2D(lists));
	}

	@Override
	public void buildCtrAllDifferentMatrix(String id, XVarInteger[][] matrix) {
		problem.allDifferentMatrix(trVars2D(matrix));
	}

	@Override
	public void buildCtrAllDifferent(String id, XNode<XVarInteger>[] trees) {
		problem.allDifferent(trVar(trees));
	}

	@Override
	public void buildCtrAllEqual(String id, XVarInteger[] list) {
		problem.allEqual(trVars(list));
	}

	// ************************************************************************
	// ***** Constraint ordered/lexicographic
	// ************************************************************************

	@Override
	public void buildCtrOrdered(String id, XVarInteger[] list, TypeOperatorRel operator) {
		problem.ordered(trVars(list), new int[list.length - 1], operator);
	}

	@Override
	public void buildCtrOrdered(String id, XVarInteger[] list, int[] lengths, TypeOperatorRel operator) {
		problem.ordered(trVars(list), lengths, operator);
	}

	@Override
	public void buildCtrOrdered(String id, XVarInteger[] list, XVarInteger[] lengths, TypeOperatorRel operator) {
		problem.ordered(trVars(list), trVars(lengths), operator);
	}

	@Override
	public void buildCtrLex(String id, XVarInteger[][] lists, TypeOperatorRel operator) {
		problem.lex(trVars2D(lists), operator);
	}

	@Override
	public void buildCtrLexMatrix(String id, XVarInteger[][] matrix, TypeOperatorRel operator) {
		problem.lexMatrix(trVars2D(matrix), operator);
	}

	// ************************************************************************
	// ***** Constraint sum
	// ************************************************************************

	@Override
	public void buildCtrSum(String id, XVarInteger[] list, Condition condition) {
		problem.sum(trVars(list), repeat(1, list.length), trVar(condition));
	}

	@Override
	public void buildCtrSum(String id, XVarInteger[] list, int[] coeffs, Condition condition) {
		problem.sum(trVars(list), coeffs, trVar(condition));
	}

	@Override
	public void buildCtrSum(String id, XVarInteger[] list, XVarInteger[] coeffs, Condition condition) {
		problem.sum(trVars(list), trVars(coeffs), trVar(condition));
	}

	@Override
	public void buildCtrSum(String id, XNode<XVarInteger>[] trees, Condition condition) {
		problem.sum(trVar(trees), repeat(1, trees.length), trVar(condition));
	}

	@Override
	public void buildCtrSum(String id, XNode<XVarInteger>[] trees, int[] coeffs, Condition condition) {
		assert coeffs != null;
		problem.sum(trVar(trees), coeffs, trVar(condition));
	}

	@Override
	public void buildCtrSum(String id, XNode<XVarInteger>[] trees, XVarInteger[] coeffs, Condition condition) {
		unimplementedCase(id, join(trees), join(coeffs), condition); // TODO
	}

	// ************************************************************************
	// ***** Constraint count
	// ************************************************************************

	@Override
	public void buildCtrCount(String id, XVarInteger[] list, int[] values, Condition condition) {
		problem.count(trVars(list), values, trVar(condition));
	}

	@Override
	public void buildCtrCount(String id, XVarInteger[] list, XVarInteger[] values, Condition condition) {
		problem.count(trVars(list), trVars(values), trVar(condition));
	}

	@Override
	public void buildCtrAtLeast(String id, XVarInteger[] list, int value, int k) {
		problem.count(trVars(list), new int[] { value }, condition(GE, k));
	}

	@Override
	public void buildCtrAtMost(String id, XVarInteger[] list, int value, int k) {
		problem.count(trVars(list), new int[] { value }, condition(LE, k));
	}

	@Override
	public void buildCtrExactly(String id, XVarInteger[] list, int value, int k) {
		problem.count(trVars(list), new int[] { value }, condition(EQ, k));
	}

	@Override
	public void buildCtrExactly(String id, XVarInteger[] list, int value, XVarInteger k) {
		problem.count(trVars(list), new int[] { value }, trVar(condition(EQ, k)));
	}

	@Override
	public void buildCtrAmong(String id, XVarInteger[] list, int[] values, int k) {
		problem.count(trVars(list), values, condition(EQ, k));
	}

	@Override
	public void buildCtrAmong(String id, XVarInteger[] list, int[] values, XVarInteger k) {
		problem.count(trVars(list), values, trVar(condition(EQ, k)));
	}

	@Override
	public void buildCtrNValues(String id, XVarInteger[] list, Condition condition) {
		problem.nValues(trVars(list), trVar(condition));
	}

	@Override
	public void buildCtrNValuesExcept(String id, XVarInteger[] list, int[] values, Condition condition) {
		problem.nValues(trVars(list), trVar(condition), values);
	}

	@Override
	public void buildCtrNotAllEqual(String id, XVarInteger[] list) {
		buildCtrNValues(id, list, condition(GT, 1));
	}

	@Override
	public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, int[] values, XVarInteger[] occurs) {
		problem.cardinality(trVars(list), values, closed, trVars(occurs));
	}

	@Override
	public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, int[] values, int[] occurs) {
		problem.cardinality(trVars(list), values, closed, occurs);
	}

	@Override
	public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, int[] values, int[] occursMin, int[] occursMax) {
		problem.cardinality(trVars(list), values, closed, occursMin, occursMax);
	}

	@Override
	public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, XVarInteger[] values, XVarInteger[] occurs) {
		problem.cardinality(trVars(list), trVars(values), closed, trVars(occurs));
	}

	@Override
	public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, XVarInteger[] values, int[] occurs) {
		problem.cardinality(trVars(list), trVars(values), closed, occurs);
	}

	@Override
	public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, XVarInteger[] values, int[] occursMin, int[] occursMax) {
		problem.cardinality(trVars(list), trVars(values), closed, occursMin, occursMax);
	}

	@Override
	public void buildCtrMaximum(String id, XVarInteger[] list, Condition condition) {
		problem.maximum(trVars(list), trVar(condition));
	}

	@Override
	public void buildCtrMaximum(String id, XVarInteger[] list, int startIndex, XVarInteger index, TypeRank rank, Condition condition) {
		problem.maximum(trVars(list), startIndex, trVar(index), rank, trVar(condition));
	}

	@Override
	public void buildCtrMaximum(String id, XNode<XVarInteger>[] trees, Condition condition) {
		problem.maximum(trVar(trees), trVar(condition));
	}

	@Override
	public void buildCtrMinimum(String id, XVarInteger[] list, Condition condition) {
		problem.minimum(trVars(list), trVar(condition));
	}

	@Override
	public void buildCtrMinimum(String id, XVarInteger[] list, int startIndex, XVarInteger index, TypeRank rank, Condition condition) {
		problem.minimum(trVars(list), startIndex, trVar(index), rank, trVar(condition));
	}

	@Override
	public void buildCtrMinimum(String id, XNode<XVarInteger>[] trees, Condition condition) {
		problem.minimum(trVar(trees), trVar(condition));
	}

	@Override
	public void buildCtrElement(String id, XVarInteger[] list, Condition condition) {
		problem.element(trVars(list), trVar(condition));
	}

	@Override
	public void buildCtrElement(String id, XVarInteger[] list, int startIndex, XVarInteger index, TypeRank rank, Condition condition) {
		control(startIndex == 0 && rank == TypeRank.ANY);
		problem.element(trVars(list), startIndex, trVar(index), rank, trVar(condition));
	}

	@Override
	public void buildCtrElement(String id, int[] list, int startIndex, XVarInteger index, TypeRank rank, Condition condition) {
		control(rank == TypeRank.ANY);
		problem.element(list, startIndex, trVar(index), rank, trVar(condition));
	}

	@Override
	public void buildCtrElement(String id, int[][] matrix, int startRowIndex, XVarInteger rowIndex, int startColIndex, XVarInteger colIndex,
			Condition condition) {
		control(startRowIndex == 0 && startColIndex == 0);
		problem.element(matrix, startRowIndex, trVar(rowIndex), startColIndex, trVar(colIndex), trVar(condition));
	}

	@Override
	public void buildCtrElement(String id, XVarInteger[][] matrix, int startRowIndex, XVarInteger rowIndex, int startColIndex, XVarInteger colIndex,
			Condition condition) {
		control(startRowIndex == 0 && startColIndex == 0);
		problem.element(trVars2D(matrix), startRowIndex, trVar(rowIndex), startColIndex, trVar(colIndex), trVar(condition));
	}

	@Override
	public void buildCtrChannel(String id, XVarInteger[] list, int startIndex) {
		problem.channel(trVars(list), startIndex);
	}

	@Override
	public void buildCtrChannel(String id, XVarInteger[] list1, int startIndex1, XVarInteger[] list2, int startIndex2) {
		problem.channel(trVars(list1), startIndex1, trVars(list2), startIndex2);
	}

	@Override
	public void buildCtrChannel(String id, XVarInteger[] list, int startIndex, XVarInteger value) {
		problem.channel(trVars(list), startIndex, trVar(value));
	}

	@Override
	public void buildCtrStretch(String id, XVarInteger[] list, int[] values, int[] widthsMin, int[] widthsMax) {
		problem.stretch(trVars(list), values, widthsMin, widthsMax, null);
	}

	@Override
	public void buildCtrStretch(String id, XVarInteger[] list, int[] values, int[] widthsMin, int[] widthsMax, int[][] patterns) {
		problem.stretch(trVars(list), values, widthsMin, widthsMax, patterns);
	}

	@Override
	public void buildCtrNoOverlap(String id, XVarInteger[] origins, int[] lengths, boolean zeroIgnored) {
		problem.noOverlap(trVars(origins), lengths, zeroIgnored);
	}

	@Override
	public void buildCtrNoOverlap(String id, XVarInteger[] origins, XVarInteger[] lengths, boolean zeroIgnored) {
		problem.noOverlap(trVars(origins), trVars(lengths), zeroIgnored);
	}

	@Override
	public void buildCtrNoOverlap(String id, XVarInteger[][] origins, int[][] lengths, boolean zeroIgnored) {
		problem.noOverlap(trVars2D(origins), lengths, zeroIgnored);
	}

	@Override
	public void buildCtrNoOverlap(String id, XVarInteger[][] origins, XVarInteger[][] lengths, boolean zeroIgnored) {
		problem.noOverlap(trVars2D(origins), trVars2D(lengths), zeroIgnored);
	}

	@Override
	public void buildCtrCumulative(String id, XVarInteger[] origins, int[] lengths, int[] heights, Condition condition) {
		problem.cumulative(trVars(origins), lengths, null, heights, trVar(condition));
	}

	@Override
	public void buildCtrCumulative(String id, XVarInteger[] origins, int[] lengths, XVarInteger[] heights, Condition condition) {
		problem.cumulative(trVars(origins), lengths, null, trVars(heights), trVar(condition));
	}

	@Override
	public void buildCtrCumulative(String id, XVarInteger[] origins, XVarInteger[] lengths, int[] heights, Condition condition) {
		problem.cumulative(trVars(origins), trVars(lengths), null, heights, trVar(condition));
	}

	@Override
	public void buildCtrCumulative(String id, XVarInteger[] origins, XVarInteger[] lengths, XVarInteger[] heights, Condition condition) {
		problem.cumulative(trVars(origins), trVars(lengths), null, trVars(heights), trVar(condition));
	}

	@Override
	public void buildCtrCumulative(String id, XVarInteger[] origins, int[] lengths, XVarInteger[] ends, int[] heights, Condition condition) {
		problem.cumulative(trVars(origins), lengths, trVars(ends), heights, trVar(condition));
	}

	@Override
	public void buildCtrCumulative(String id, XVarInteger[] origins, int[] lengths, XVarInteger[] ends, XVarInteger[] heights, Condition condition) {
		problem.cumulative(trVars(origins), lengths, trVars(ends), trVars(heights), trVar(condition));
	}

	@Override
	public void buildCtrCumulative(String id, XVarInteger[] origins, XVarInteger[] lengths, XVarInteger[] ends, int[] heights, Condition condition) {
		problem.cumulative(trVars(origins), trVars(lengths), trVars(ends), heights, trVar(condition));
	}

	@Override
	public void buildCtrCumulative(String id, XVarInteger[] origins, XVarInteger[] lengths, XVarInteger[] ends, XVarInteger[] heights, Condition condition) {
		problem.cumulative(trVars(origins), trVars(lengths), trVars(ends), trVars(heights), trVar(condition));
	}

	@Override
	public void buildCtrInstantiation(String id, XVarInteger[] list, int[] values) {
		problem.instantiation(trVars(list), values);
	}

	@Override
	public void buildCtrClause(String id, XVarInteger[] pos, XVarInteger[] neg) {
		problem.clause(trVars(pos), trVars(neg));
	}

	@Override
	public void buildCtrCircuit(String id, XVarInteger[] list, int startIndex) {
		problem.circuit(trVars(list), startIndex);
	}

	@Override
	public void buildBinPacking(String id, XVarInteger[] list, int[] sizes, Condition condition) {
		problem.binpacking(trVars(list), sizes, trVar(condition));
	}

	/**********************************************************************************************
	 * Methods for loading objectives
	 *********************************************************************************************/

	@Override
	public void loadObj(XObj o) {
		if (problem.features.mustDiscard(o.vars()))
			return;
		XCallbacks2.super.loadObj(o);
		CtrEntity entity = problem.ctrEntities.allEntities.get(problem.ctrEntities.allEntities.size() - 1);
		copyBasicAttributes(entity, o);
	}

	@Override
	public void buildObjToMinimize(String id, XVarInteger x) {
		problem.minimize(trVar(x));
	}

	@Override
	public void buildObjToMaximize(String id, XVarInteger x) {
		problem.maximize(trVar(x));
	}

	static class VarVal {
		Variable x;
		int a;

		VarVal(Variable x, int a) {
			this.x = x;
			this.a = a;
		}
	}

	private List<VarVal> isSum(XNodeParent<XVarInteger> tree) {
		List<VarVal> list = new ArrayList<>();
		if (tree.type == TypeExpr.ADD)
			for (XNode<XVarInteger> son : tree.sons) {
				if (son.type == TypeExpr.VAR)
					list.add(new VarVal(trVar(son.var(0)), 1));
				else if (MatcherInterface.x_mul_k.matches(son) || MatcherInterface.k_mul_x.matches(son))
					list.add(new VarVal(trVar(son.var(0)), son.val(0)));
				else {
					list.clear();
					break;
				}
			}
		return list;
	}

	@Override
	public void buildObjToMinimize(String id, XNodeParent<XVarInteger> tree) {
		List<VarVal> list = isSum(tree);
		if (list.size() > 0)
			problem.minimize(SUM, list.stream().map(vv -> vv.x).toArray(VariableInteger[]::new), list.stream().mapToInt(vv -> vv.a).toArray());
		else
			problem.minimize(trVar(tree));
	}

	@Override
	public void buildObjToMaximize(String id, XNodeParent<XVarInteger> tree) {
		List<VarVal> list = isSum(tree);
		if (list.size() > 0)
			problem.maximize(SUM, list.stream().map(vv -> vv.x).toArray(VariableInteger[]::new), list.stream().mapToInt(vv -> vv.a).toArray());
		else
			problem.maximize(trVar(tree));
	}

	@Override
	public void buildObjToMinimize(String id, TypeObjective type, XVarInteger[] list) {
		problem.minimize(type, trVars(list));
	}

	@Override
	public void buildObjToMaximize(String id, TypeObjective type, XVarInteger[] list) {
		problem.maximize(type, trVars(list));
	}

	@Override
	public void buildObjToMinimize(String id, TypeObjective type, XVarInteger[] list, int[] coeffs) {
		problem.minimize(type, trVars(list), coeffs);
	}

	@Override
	public void buildObjToMaximize(String id, TypeObjective type, XVarInteger[] list, int[] coeffs) {
		problem.maximize(type, trVars(list), coeffs);
	}

	@Override
	public void buildObjToMinimize(String id, TypeObjective type, XNode<XVarInteger>[] trees) {
		problem.minimize(type, trVar(trees));
	}

	@Override
	public void buildObjToMaximize(String id, TypeObjective type, XNode<XVarInteger>[] trees) {
		problem.maximize(type, trVar(trees));
	}

	@Override
	public void buildObjToMinimize(String id, TypeObjective type, XNode<XVarInteger>[] trees, int[] coeffs) {
		problem.minimize(type, trVar(trees), coeffs);
	}

	@Override
	public void buildObjToMaximize(String id, TypeObjective type, XNode<XVarInteger>[] trees, int[] coeffs) {
		problem.maximize(type, trVar(trees), coeffs);
	}

	/**********************************************************************************************
	 ***** Methods to be implemented on symbolic variables/constraints
	 *********************************************************************************************/

	@Override
	public void buildCtrIntension(String id, XVarSymbolic[] scope, XNodeParent<XVarSymbolic> tree) {
		control(tree.exactlyVars(scope), "Pb with scope");
		problem.intension((XNodeParent<IVar>) trVarSymbolic(tree));
		// problem.intension(trVars(scope), (XNodeParent<IVar>) (Object) tree);
	}

	@Override
	public void buildCtrExtension(String id, XVarSymbolic x, String[] values, boolean positive, Set<TypeFlag> flags) {
		control(!flags.contains(TypeFlag.STARRED_TUPLES), "Forbidden for unary constraints");
		values = flags.contains(TypeFlag.UNCLEAN_TUPLES) ? Variable.filterValues(trVar(x), values) : values;
		problem.extension(vars(trVar(x)), dub(values), positive);
	}

	@Override
	public void buildCtrExtension(String id, XVarSymbolic[] list, String[][] tuples, boolean positive, Set<TypeFlag> flags) {
		tuples = flags.contains(TypeFlag.UNCLEAN_TUPLES) ? Variable.filterTuples(trVars(list), tuples) : tuples;
		problem.extension(trVars(list), tuples, positive, flags.contains(TypeFlag.STARRED_TUPLES));
	}

	@Override
	public void buildCtrAllDifferent(String id, XVarSymbolic[] list) {
		problem.allDifferent(trVars(list));
	}

	/**********************************************************************************************
	 * Methods to be implemented on Annotations
	 *********************************************************************************************/

	@Override
	public void buildAnnotationDecision(XVarInteger[] list) {
		decisionVariables(trVars(list));
	}

}
