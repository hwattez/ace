/**
 * AbsCon - Copyright (c) 2017, CRIL-CNRS - lecoutre@cril.fr
 * 
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the CONTRAT DE LICENCE DE LOGICIEL LIBRE CeCILL which accompanies this
 * distribution, and is available at http://www.cecill.info
 */
package constraints.hard.primitive;

import java.math.BigInteger;

import org.xcsp.common.Types.TypeConditionOperatorRel;
import org.xcsp.common.Utilities;
import org.xcsp.modeler.entities.CtrEntities.CtrAlone;

import interfaces.TagFilteringCompleteAtEachCall;
import interfaces.TagGACGuaranteed;
import interfaces.TagSymmetric;
import interfaces.TagUnsymmetric;
import problem.Problem;
import utility.Kit;
import utility.exceptions.UnreachableCodeException;
import variables.Variable;
import variables.domains.Domain;

public abstract class CtrPrimitiveBinary extends CtrPrimitive implements TagGACGuaranteed, TagFilteringCompleteAtEachCall {

	public static boolean enforceLT(Domain dx, Domain dy) { // x < y
		return dx.removeValuesGreaterThanOrEqual(dy.lastValue()) && dy.removeValuesLessThanOrEqual(dx.firstValue());
	}

	public static boolean enforceLT(Domain dx, Domain dy, int k) { // x < y + k
		return dx.removeValuesGreaterThanOrEqual(dy.lastValue() + k) && dy.removeValuesLessThanOrEqual(dx.firstValue() - k);
	}

	public static boolean enforceLE(Domain dx, Domain dy) { // x <= y
		return dx.removeValuesGreaterThan(dy.lastValue()) && dy.removeValuesLessThan(dx.firstValue());
	}

	public static boolean enforceLE(Domain dx, Domain dy, int k) { // x <= y + k
		return dx.removeValuesGreaterThan(dy.lastValue() + k) && dy.removeValuesLessThan(dx.firstValue() - k);
	}

	public static boolean enforceGE(Domain dx, Domain dy) { // x >= y
		return dx.removeValuesLessThan(dy.firstValue()) && dy.removeValuesGreaterThan(dx.lastValue());
	}

	public static boolean enforceGE(Domain dx, Domain dy, int k) { // x >= y + k
		return dx.removeValuesLessThan(dy.firstValue() + k) && dy.removeValuesGreaterThan(dx.lastValue() - k);
	}

	public static boolean enforceGT(Domain dx, Domain dy) { // x > y
		return dx.removeValuesLessThanOrEqual(dy.firstValue()) && dy.removeValuesGreaterThanOrEqual(dx.lastValue());
	}

	public static boolean enforceGT(Domain dx, Domain dy, int k) { // x > y + k
		return dx.removeValuesLessThanOrEqual(dy.firstValue() + k) && dy.removeValuesGreaterThanOrEqual(dx.lastValue() - k);
	}

	public static boolean enforceEQ(Domain dx, Domain dy) { // x = y
		if (dx.removeValuesNotIn(dy) == false)
			return false;
		if (dx.size() == dy.size())
			return true;
		assert dx.size() < dy.size();
		boolean consistent = dy.removeValuesNotIn(dx);
		assert consistent;
		return true;
	}

	public static boolean enforceEQ(Domain dx, Domain dy, int k) { // x = y + k
		if (dx.removeValuesNotIn(dy, k) == false)
			return false;
		if (dx.size() == dy.size())
			return true;
		assert dx.size() < dy.size();
		boolean consistent = dy.removeValuesNotIn(dx, -k);
		assert consistent;
		return true;
	}

	public static boolean enforceNE(Domain dx, Domain dy) { // x != y
		if (dx.size() == 1 && dy.removeValueIfPresent(dx.uniqueValue()) == false)
			return false;
		if (dy.size() == 1 && dx.removeValueIfPresent(dy.uniqueValue()) == false)
			return false;
		return true;
	}

	public static boolean enforceNE(Domain dx, Domain dy, int k) { // x != y + k
		if (dx.size() == 1 && dy.removeValueIfPresent(dx.uniqueValue() - k) == false)
			return false;
		if (dy.size() == 1 && dx.removeValueIfPresent(dy.uniqueValue() + k) == false)
			return false;
		return true;
	}

	public static int commonValueIn(Domain dx, Domain dy) {
		if (dx.size() <= dy.size())
			for (int a = dx.first(); a != -1; a = dx.next(a)) {
				int va = dx.toVal(a);
				if (dy.isPresentValue(va))
					return va;
			}
		else
			for (int b = dy.first(); b != -1; b = dy.next(b)) {
				int vb = dy.toVal(b);
				if (dx.isPresentValue(vb))
					return vb;
			}
		return Integer.MAX_VALUE;
	}

	protected final Variable x, y;

	protected final Domain dx, dy;

	public CtrPrimitiveBinary(Problem pb, Variable x, Variable y) {
		super(pb, pb.api.vars(x, y));
		this.x = x;
		this.y = y;
		this.dx = x.dom;
		this.dy = y.dom;
	}

	public static abstract class CtrPrimitiveBinaryWithCst extends CtrPrimitiveBinary {

		protected final int k;

		public CtrPrimitiveBinaryWithCst(Problem pb, Variable x, Variable y, int k) {
			super(pb, x, y);
			this.k = k;
			defineKey(k);
		}
	}

	// ************************************************************************
	// ***** Class Disjonctive
	// ************************************************************************

	public static final class Disjonctive extends CtrPrimitiveBinary {

		final int kx, ky;

		@Override
		public boolean checkValues(int[] t) {
			return t[0] + kx <= t[1] || t[1] + ky <= t[0];
		}

		@Override
		public Boolean isSymmetric() {
			return kx == ky;
		}

		public Disjonctive(Problem pb, Variable x, int kx, Variable y, int ky) {
			super(pb, x, y);
			this.kx = kx;
			this.ky = ky;
			defineKey(kx, ky);
		}

		@Override
		public boolean runPropagator(Variable dummy) {
			return dx.removeValuesInRange(dy.lastValue() - kx + 1, dy.firstValue() + ky)
					&& dy.removeValuesInRange(dx.lastValue() - ky + 1, dx.firstValue() + kx);
		}
	}

	// ************************************************************************
	// ***** Classes for x + y <op> k (CtrPrimitiveBinaryAdd)
	// ************************************************************************

	public static abstract class CtrPrimitiveBinaryAdd extends CtrPrimitiveBinaryWithCst implements TagSymmetric {

		public static CtrAlone buildFrom(Problem pb, Variable x, Variable y, TypeConditionOperatorRel op, int k) {
			switch (op) {
			case LT:
				return pb.addCtr(new AddLE2(pb, x, y, k - 1));
			case LE:
				return pb.addCtr(new AddLE2(pb, x, y, k));
			case GE:
				return pb.addCtr(new AddGE2(pb, x, y, k));
			case GT:
				return pb.addCtr(new AddGE2(pb, x, y, k + 1));
			case EQ:
				return pb.addCtr(new AddEQ2(pb, x, y, k)); // return pb.extension(eq(add(x, y), k));
			case NE:
				return pb.addCtr(new AddNE2(pb, x, y, k));
			}
			throw new UnreachableCodeException();
		}

		public CtrPrimitiveBinaryAdd(Problem pb, Variable x, Variable y, int k) {
			super(pb, x, y, k);
		}

		public static final class AddLE2 extends CtrPrimitiveBinaryAdd {

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] + t[1] <= k;
			}

			public AddLE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return dx.removeValuesGreaterThan(k - dy.firstValue()) && dy.removeValuesGreaterThan(k - dx.firstValue());
			}
		}

		public static final class AddGE2 extends CtrPrimitiveBinaryAdd {

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] + t[1] >= k;
			}

			public AddGE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return dx.removeValuesLessThan(k - dy.lastValue()) && dy.removeValuesLessThan(k - dx.lastValue());
			}
		}

		public static final class AddEQ2 extends CtrPrimitiveBinaryAdd implements TagUnsymmetric {

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] + t[1] == k;
			}

			public AddEQ2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return dx.removeValuesAtOffsetNE(k, dy) && dy.removeValuesAtOffsetNE(k, dx);
			}
		}

		public static final class AddNE2 extends CtrPrimitiveBinaryAdd {

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] + t[1] != k;
			}

			public AddNE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public int giveUpperBoundOfMaxNumberOfConflictsFor(Variable x, int a) {
				return 1;
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				if (dx.size() == 1 && dy.removeValueIfPresent(k - dx.uniqueValue()) == false)
					return false;
				if (dy.size() == 1 && dx.removeValueIfPresent(k - dy.uniqueValue()) == false)
					return false;
				return true;
			}
		}
	}
	// ************************************************************************
	// ***** Classes for x - y <op> k (CtrPrimitiveBinarySub)
	// ************************************************************************

	public static abstract class CtrPrimitiveBinarySub extends CtrPrimitiveBinaryWithCst {

		public static CtrAlone buildFrom(Problem pb, Variable x, Variable y, TypeConditionOperatorRel op, int k) {
			switch (op) {
			case LT:
				return pb.addCtr(new SubLE2(pb, x, y, k - 1));
			case LE:
				return pb.addCtr(new SubLE2(pb, x, y, k));
			case GE:
				return pb.addCtr(new SubGE2(pb, x, y, k));
			case GT:
				return pb.addCtr(new SubGE2(pb, x, y, k + 1));
			case EQ:
				return pb.addCtr(new SubEQ2(pb, x, y, k)); // return pb.extension(eq(sub(x, y), k));
			case NE:
				return pb.addCtr(new SubNE2(pb, x, y, k));
			}
			throw new UnreachableCodeException();
		}

		public CtrPrimitiveBinarySub(Problem pb, Variable x, Variable y, int k) {
			super(pb, x, y, k);
		}

		public static final class SubLE2 extends CtrPrimitiveBinarySub implements TagUnsymmetric {

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] - t[1] <= k;
			}

			public SubLE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return enforceLE(dx, dy, k);
			}
		}

		public static final class SubGE2 extends CtrPrimitiveBinarySub implements TagUnsymmetric {

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] - t[1] >= k;
			}

			public SubGE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return enforceGE(dx, dy, k);
			}
		}

		public static final class SubEQ2 extends CtrPrimitiveBinarySub {

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] - t[1] == k;
			}

			public SubEQ2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public Boolean isSymmetric() {
				return k == 0;
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return enforceEQ(dx, dy, k);
			}
		}

		public static final class SubNE2 extends CtrPrimitiveBinarySub {

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] - t[1] != k;
			}

			public SubNE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public Boolean isSymmetric() {
				return k == 0;
			}

			@Override
			public int giveUpperBoundOfMaxNumberOfConflictsFor(Variable x, int a) {
				return 1;
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return enforceNE(dx, dy, k);
			}
		}
	}

	// ************************************************************************
	// ***** Classes for x * y <op> k (CtrPrimitiveBinaryMul)
	// ************************************************************************

	public static abstract class CtrPrimitiveBinaryMul extends CtrPrimitiveBinaryWithCst implements TagSymmetric {

		public static CtrAlone buildFrom(Problem pb, Variable x, Variable y, TypeConditionOperatorRel op, int k) {
			// switch (op) {
			// case LT:
			// return pb.extension(lt(dist(x, y), k));
			// case LE:
			// return pb.extension(le(dist(x, y), k));
			// case GE:
			// return pb.addCtr(new DistGE2(pb, x, y, k));
			// case GT:
			// return pb.addCtr(new DistGE2(pb, x, y, k + 1));
			// case EQ:
			// return pb.addCtr(new DistEQ2(pb, x, y, k)); // return pb.extension(eq(dist(x, y), k));
			// case NE:
			// return pb.addCtr(new DistNE2(pb, x, y, k));
			// }
			throw new UnreachableCodeException();
		}

		public CtrPrimitiveBinaryMul(Problem pb, Variable x, Variable y, int k) {
			super(pb, x, y, k);
			Kit.control(Utilities.isSafeInt(BigInteger.valueOf(dx.firstValue()).multiply(BigInteger.valueOf(dy.firstValue())).longValueExact()));
			Kit.control(Utilities.isSafeInt(BigInteger.valueOf(dx.lastValue()).multiply(BigInteger.valueOf(dy.lastValue())).longValueExact()));
		}

		public static final class MulLE2 extends CtrPrimitiveBinaryMul {

			@Override
			public boolean checkValues(int[] t) {
				return t[0] * t[1] <= k;
			}

			public MulLE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				// if (k >= 0) {
				//
				//
				// }
				// for (int a = dx.last(); a != -1; a = dx.prev(a)) {
				// int va = dx.toVal(a);
				// if (va < 0)
				// break;
				// if (va*dy.firstValue() <= k) {
				// if (dy.firstValue() >=
				// }
				// if (va*dy.firstValue() > k && dx.remove(a) == false)
				// return false;
				//
				// if (!dy.isPresentValue(va + k) && !dy.isPresentValue(va - k) && dx.remove(a) == false)
				// return false;
				// }
				//

				return true;
			}
		}
	}

	// ************************************************************************
	// ***** Classes for |x - y| <op> k (CtrPrimitiveBinaryDist)
	// ************************************************************************

	public static abstract class CtrPrimitiveBinaryDist extends CtrPrimitiveBinaryWithCst implements TagSymmetric {

		public static CtrAlone buildFrom(Problem pb, Variable x, Variable y, TypeConditionOperatorRel op, int k) {
			switch (op) {
			case LT:
				return pb.addCtr(new DistLE2(pb, x, y, k - 1)); // return pb.extension(lt(dist(x, y), k));
			case LE:
				return pb.addCtr(new DistLE2(pb, x, y, k)); // return pb.extension(le(dist(x, y), k));
			case GE:
				return pb.addCtr(new DistGE2(pb, x, y, k));
			case GT:
				return pb.addCtr(new DistGE2(pb, x, y, k + 1));
			case EQ:
				return pb.addCtr(new DistEQ2(pb, x, y, k)); // return pb.extension(eq(dist(x, y), k));
			// ok for java ac csp/Rlfap-scen-11-f06.xml.lzma -cm -ev -varh=Dom but not for domOnwdeg. Must be because of failing may occur on assigned
			// variables in DISTEQ2
			case NE:
				return pb.addCtr(new DistNE2(pb, x, y, k));
			}
			throw new UnreachableCodeException();
		}

		public CtrPrimitiveBinaryDist(Problem pb, Variable x, Variable y, int k) {
			super(pb, x, y, k);
			Utilities.control(k > 0, "k should be strictly positive");
		}

		public static final class DistLE2 extends CtrPrimitiveBinaryDist {

			@Override
			public boolean checkValues(int[] t) {
				return Math.abs(t[0] - t[1]) <= k;
			}

			public DistLE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return dx.removeValuesAtDistanceGT(k, dy) && dy.removeValuesAtDistanceGT(k, dx);
			}
		}

		public static final class DistGE2 extends CtrPrimitiveBinaryDist { // code similar to Disjunctive

			@Override
			public boolean checkValues(int[] t) {
				return Math.abs(t[0] - t[1]) >= k; // equivalent to disjunctive: t[0] + k <= t[1] || t[1] + k <= t[0];
			}

			public DistGE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return dx.removeValuesInRange(dy.lastValue() - k + 1, dy.firstValue() + k)
						&& dy.removeValuesInRange(dx.lastValue() - k + 1, dx.firstValue() + k);
			}
		}

		public static final class DistEQ2 extends CtrPrimitiveBinaryDist {

			@Override
			public final boolean checkValues(int[] t) {
				return Math.abs(t[0] - t[1]) == k;
			}

			public DistEQ2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return dx.removeValuesAtDistanceNE(k, dy) && dy.removeValuesAtDistanceNE(k, dx);
			}
		}

		public static final class DistNE2 extends CtrPrimitiveBinaryDist {

			@Override
			public final boolean checkValues(int[] t) {
				return Math.abs(t[0] - t[1]) != k;
			}

			public DistNE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			private boolean revise(Domain dom1, Domain dom2) {
				if (dom1.size() == 1)
					return dom2.removeValueIfPresent(dom1.uniqueValue() - k) && dom2.removeValueIfPresent(dom1.uniqueValue() + k);
				if (dom1.size() == 2 && dom1.intervalValue() == 2 * k)
					return dom2.removeValueIfPresent(dom1.lastValue() - k);
				return true;
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				return revise(dx, dy) && revise(dy, dx);
			}
		}
	}

	// ************************************************************************
	// ***** Classes for x = (y <op> k) (CtrPrimitiveBinaryLog)
	// ************************************************************************

	public static abstract class CtrPrimitiveBinaryLog extends CtrPrimitiveBinaryWithCst implements TagUnsymmetric {

		public static CtrAlone buildFrom(Problem pb, Variable x, Variable y, TypeConditionOperatorRel op, int k) {
			switch (op) {
			case LT:
				return pb.addCtr(new LogLE2(pb, x, y, k - 1));
			case LE:
				return pb.addCtr(new LogLE2(pb, x, y, k));
			case GE:
				return pb.addCtr(new LogGE2(pb, x, y, k));
			case GT:
				return pb.addCtr(new LogGE2(pb, x, y, k + 1));
			case EQ:
				return pb.addCtr(new LogEQ2(pb, x, y, k));
			case NE:
				return pb.addCtr(new LogNE2(pb, x, y, k));
			}
			throw new UnreachableCodeException();
		}

		public CtrPrimitiveBinaryLog(Problem pb, Variable x, Variable y, int k) {
			super(pb, x, y, k);
			Utilities.control(dx.is01(), "The first variable should be of type 01");
		}

		public static final class LogLE2 extends CtrPrimitiveBinaryLog {

			@Override
			public final boolean checkValues(int[] t) {
				return (t[0] == 1) == (t[1] <= k);
			}

			public LogLE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				if (dx.first() == 0 && dy.lastValue() <= k && dx.remove(0) == false)
					return false;
				if (dx.last() == 1 && dy.firstValue() > k && dx.remove(1) == false)
					return false;
				if (dx.first() == 1 && dy.lastValue() > k && dy.removeValuesGreaterThan(k) == false)
					return false;
				if (dx.last() == 0 && dy.firstValue() <= k && dy.removeValuesLessThanOrEqual(k) == false)
					return false;
				return true;
			}
		}

		public static final class LogGE2 extends CtrPrimitiveBinaryLog {

			@Override
			public final boolean checkValues(int[] t) {
				return (t[0] == 1) == (t[1] >= k);
			}

			public LogGE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				if (dx.first() == 0 && dy.firstValue() >= k && dx.remove(0) == false)
					return false;
				if (dx.last() == 1 && dy.lastValue() < k && dx.remove(1) == false)
					return false;
				if (dx.first() == 1 && dy.firstValue() < k && dy.removeValuesLessThan(k) == false)
					return false;
				if (dx.last() == 0 && dy.lastValue() >= k && dy.removeValuesGreaterThanOrEqual(k) == false)
					return false;
				return true;
			}
		}

		public static final class LogEQ2 extends CtrPrimitiveBinaryLog {

			@Override
			public final boolean checkValues(int[] t) {
				return (t[0] == 1) == (t[1] == k);
			}

			public LogEQ2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				if (!dy.isPresentValue(k)) {
					if (dx.removeIfPresent(1) == false)
						return false;
				} else if (dy.size() == 1) {
					if (dx.removeIfPresent(0) == false)
						return false;
				}
				if (dx.size() == 1) {
					if (dx.first() == 0 && dy.removeValueIfPresent(k) == false)
						return false;
					if (dx.first() == 1 && dy.reduceToValue(k) == false)
						return false;
				}
				return true;
			}
		}

		public static final class LogNE2 extends CtrPrimitiveBinaryLog {

			@Override
			public final boolean checkValues(int[] t) {
				return (t[0] == 1) == (t[1] != k);
			}

			public LogNE2(Problem pb, Variable x, Variable y, int k) {
				super(pb, x, y, k);
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				if (!dy.isPresentValue(k)) {
					if (dx.removeIfPresent(0) == false)
						return false;
				} else if (dy.size() == 1) {
					if (dx.removeIfPresent(1) == false)
						return false;
				}
				if (dx.size() == 1) {
					if (dx.first() == 0 && dy.reduceToValue(k) == false)
						return false;
					if (dx.first() == 1 && dy.removeValueIfPresent(k) == false)
						return false;
				}
				return true;
			}
		}

	}
}
