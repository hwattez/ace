/**
 * AbsCon - Copyright (c) 2017, CRIL-CNRS - lecoutre@cril.fr
 * 
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the CONTRAT DE LICENCE DE LOGICIEL LIBRE CeCILL which accompanies this
 * distribution, and is available at http://www.cecill.info
 */
package constraints.hard;

import static org.xcsp.common.Constants.STAR;
import static org.xcsp.common.Constants.STAR_SYMBOL;
import static org.xcsp.modeler.definitions.IRootForCtrAndObj.map;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.xcsp.modeler.definitions.ICtr.ICtrExtension;

import constraints.Constraint;
import constraints.TupleManager;
import constraints.hard.extension.CtrExtensionMDDShort;
import constraints.hard.extension.structures.ExtensionStructure;
import constraints.hard.extension.structures.Table;
import constraints.hard.extension.structures.TableWithSubtables;
import constraints.hard.extension.structures.Tries;
import interfaces.FilteringGlobal;
import interfaces.ObserverBacktracking.ObserverBacktrackingSystematic;
import interfaces.TagFilteringCompleteAtEachCall;
import interfaces.TagGACGuaranteed;
import interfaces.TagNegative;
import interfaces.TagPositive;
import interfaces.TagShort;
import problem.Problem;
import propagation.structures.supporters.SupporterHard;
import utility.Kit;
import utility.Reflector;
import variables.Variable;
import variables.Variable.VariableInteger;
import variables.Variable.VariableSymbolic;

public abstract class CtrExtension extends Constraint implements TagGACGuaranteed, TagFilteringCompleteAtEachCall, ICtrExtension {

	/**********************************************************************************************
	 ***** Generic and Global classes
	 *********************************************************************************************/

	/**
	 * Involves iterating lists of valid tuples in order to find a support.
	 */
	public static final class CtrExtensionV extends CtrExtension {

		@Override
		protected ExtensionStructure buildExtensionStructure() {
			if (scp.length == 2)
				return Reflector.buildObject(pb.rs.cp.settingExtension.classForBinaryExtensionStructure, ExtensionStructure.class, this);
			if (scp.length == 3)
				return Reflector.buildObject(pb.rs.cp.settingExtension.classForTernaryExtensionStructure, ExtensionStructure.class, this);
			return new Table(this); // MDD(this);
		}

		public CtrExtensionV(Problem pb, Variable[] scp) {
			super(pb, scp);
		}
	}

	public static final class CtrExtensionVA extends CtrExtension implements TagPositive {

		@Override
		protected ExtensionStructure buildExtensionStructure() {
			if (pb.rs.cp.settingExtension.variant == 0)
				return new TableWithSubtables(this);
			assert pb.rs.cp.settingExtension.variant == 1 || pb.rs.cp.settingExtension.variant == 11;
			return new Tries(this, pb.rs.cp.settingExtension.variant == 11);
		}

		public CtrExtensionVA(Problem pb, Variable[] scp) {
			super(pb, scp);
		}

		private final boolean seekSupportVA(int x, int a, int[] tuple, boolean another) {
			if (!another)
				tupleManager.firstValidTupleWith(x, a, tuple);
			else if (tupleManager.nextValidTupleCautiously() == -1)
				return false;
			while (true) {
				int[] t = extStructure.nextSupport(x, a, tuple);
				if (t == tuple)
					break;
				if (t == null)
					return false;
				Kit.copy(t, tuple);
				if (isValid(tuple))
					break;
				if (tupleManager.nextValidTupleCautiously() == -1)
					return false;
			}
			return true;
		}

		@Override
		public final boolean seekFirstSupportWith(int x, int a, int[] buffer) {
			buffer[x] = a;
			return seekSupportVA(x, a, buffer, false);
		}
	}

	public abstract static class CtrExtensionGlobal extends CtrExtension implements FilteringGlobal, ObserverBacktrackingSystematic {

		public CtrExtensionGlobal(Problem pb, Variable[] scp) {
			super(pb, scp);
		}
	}

	/**********************************************************************************************
	 ***** Static
	 *********************************************************************************************/

	private static CtrExtension build(Problem pb, Variable[] scp, boolean positive, boolean presentStar) {
		Set<Class<?>> classes = pb.rs.handlerClasses.map.get(CtrExtension.class);
		if (presentStar) {
			Kit.control(positive);
			CtrExtension c = (CtrExtension) Reflector.buildObject2(CtrExtension.class.getSimpleName() + pb.rs.cp.settingExtension.positive, classes, pb, scp);
			Kit.control(c instanceof TagShort); // currently, STR2, STR2S, CT, CT2 and MDDSHORT
			return c;
		}
		if (scp.length == 1 || scp.length == 2 && pb.rs.cp.settingExtension.validForBinary)
			return new CtrExtensionV(pb, scp); // return new CtrExtensionSTR2(pb, scp);
		String suffix = (positive ? pb.rs.cp.settingExtension.positive : pb.rs.cp.settingExtension.negative).toString();
		return (CtrExtension) Reflector.buildObject2(CtrExtension.class.getSimpleName() + suffix, classes, pb, scp);
	}

	private static int[][] reverseTuples(Variable[] variables, int[][] tuples) {
		Kit.control(Variable.areDomainsFull(variables));
		assert Kit.isLexIncreasing(tuples);
		int cnt = 0;
		TupleManager tupleManager = new TupleManager(variables);
		int[] idxs = tupleManager.firstValidTuple(), vals = new int[idxs.length];
		List<int[]> list = new ArrayList<>();
		do {
			for (int i = vals.length - 1; i >= 0; i--)
				vals[i] = variables[i].dom.toVal(idxs[i]);
			if (cnt < tuples.length && Arrays.equals(vals, tuples[cnt]))
				cnt++;
			else
				list.add(vals.clone());
		} while (tupleManager.nextValidTuple() != -1);
		return Kit.intArray2D(list);
	}

	private static boolean isStarPresent(Object tuples) {
		return tuples instanceof int[][] ? Kit.isPresent(STAR, (int[][]) tuples) : Kit.isPresent(STAR_SYMBOL, (String[][]) tuples);
	}

	public static CtrExtension build(Problem pb, Variable[] scp, Object tuples, boolean positive, Boolean starred) {
		Kit.control(Variable.haveSameType(scp));
		Kit.control(Array.getLength(tuples) == 0 || Array.getLength(Array.get(tuples, 0)) == scp.length,
				() -> "Badly formed extensional constraint " + scp.length + " " + Array.getLength(tuples));
		if (starred == null)
			starred = isStarPresent(tuples);
		else
			assert starred == isStarPresent(tuples) : starred + " \n" + Kit.join(tuples);
		CtrExtension c = build(pb, scp, positive, starred);

		int[][] m = null;
		if (scp[0] instanceof VariableSymbolic) {
			m = pb.symbolic.replaceSymbols((String[][]) tuples);
			pb.symbolic.store(c, (String[][]) tuples);
		} else {
			m = (int[][]) tuples;
			if (!starred && pb.rs.cp.settingExtension.mustReverse(scp.length, positive)) {
				m = reverseTuples(scp, m);
				positive = !positive;
			}
		}

		String stuffKey = c.signature() + " " + m + " " + positive; // TODO be careful, we assume that the address of tuples can be used
		c.key = pb.stuff.collectedTuples.computeIfAbsent(stuffKey, k -> c.signature() + "r" + pb.stuff.collectedTuples.size());
		// TODO something to modify above ; don't seem to be compatible (keys)
		c.storeTuples(m, positive);
		return c;
	}

	/**********************************************************************************************
	 * End of static section
	 *********************************************************************************************/

	protected ExtensionStructure extStructure;

	@Override
	public ExtensionStructure extStructure() {
		return extStructure;
	}

	protected abstract ExtensionStructure buildExtensionStructure();

	@Override
	public void cloneStructures(boolean onlyConflictsStructure) {
		super.cloneStructures(onlyConflictsStructure);
		if (!onlyConflictsStructure && extStructure.registeredCtrs().size() > 1) {
			extStructure.unregister(this);
			extStructure = Reflector.buildObject(extStructure.getClass().getSimpleName(), ExtensionStructure.class, this, extStructure);
			// IF NECESSARY, add another constructor in the class instance of
			// ExtensionStructure
		}
	}

	public final void storeTuples(int[][] tuples, boolean positive) {
		control((positive && this instanceof TagPositive) || (!positive && this instanceof TagNegative)
				|| (!(this instanceof TagPositive) && !(this instanceof TagNegative)), positive + " " + this.getClass().getName());
		// System.out.println("Storing tuples for " + this + " " + Kit.join(tuples) + " " + positive);

		if (supporter != null)
			((SupporterHard) supporter).reset();
		Map<String, ExtensionStructure> map = pb.rs.mapOfExtensionStructures;

		if (key == null || !map.containsKey(key)) {
			extStructure = buildExtensionStructure();
			extStructure.originalTuples = pb.rs.cp.settingProblem.isSymmetryBreaking() ? tuples : null;
			extStructure.originalPositive = positive;
			extStructure.storeTuples(tuples, positive);
			if (key != null) {
				map.put(key, extStructure);
				// below, "necessary" to let this code here because tuples and positive are easily accessible
				if (pb.rs.cp.settingProblem.isSymmetryBreaking()) {
					Constraint.putSymmetryMatching(key, extStructure.computeVariableSymmetryMatching(tuples, positive));
				}
			}
			if (!(this instanceof CtrExtensionMDDShort))
				conflictsStructure = ConflictsStructure.build(this, tuples, positive);
		} else {
			extStructure = map.get(key);
			extStructure.register(this);
			conflictsStructure = extStructure.firstRegisteredCtr().conflictsStructure();
			if (conflictsStructure != null)
				conflictsStructure.register(this);
			assert indexesMatchValues == extStructure.firstRegisteredCtr().indexesMatchValues;
		}
	}

	@Override
	public int[] defineSymmetryMatching() {
		return extStructure.computeVariableSymmetryMatching();
	}

	public CtrExtension(Problem pb, Variable[] scp) {
		super(pb, scp);
	}

	@Override
	public final boolean checkValues(int[] t) {
		return checkIndexes(toIdxs(t, tupleManager.localTuple));
	}

	/**
	 * In this overriding, we know that we can check directly indexes with the extension structure (by construction). As a result, we cannot check
	 * values anymore (see previous method).
	 */
	@Override
	public final boolean checkIndexes(int[] t) {
		return extStructure.checkIdxs(t);
	}

	@Override
	public boolean removeTuple(int... idxs) {
		if (extStructure.removeTuple(idxs)) {
			if (conflictsStructure != null)
				conflictsStructure.manageRemovedTuple(idxs);
			return true;
		}
		return false;
	}

	boolean controlTuples(int[][] tuples) {
		return Stream.of(tuples).allMatch(t -> IntStream.range(0, t.length).allMatch(i -> t[i] == STAR || scp[i].dom.isPresentValue(t[i])));
	}

	@Override
	public Map<String, Object> mapXCSP() {
		Object tuples = scp[0] instanceof VariableInteger ? extStructure.originalTuples : pb.symbolic.mapOfTuples.get(this);
		return map(SCOPE, scp, LIST, compactOrdered(scp), ARITY, scp.length, TUPLES, tuples, POSITIVE, extStructure.originalPositive);
	}

}