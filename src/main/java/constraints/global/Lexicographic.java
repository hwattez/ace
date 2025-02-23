/*
 * This file is part of the constraint solver ACE (AbsCon Essence). 
 *
 * Copyright (c) 2021. All rights reserved.
 * Christophe Lecoutre, CRIL, Univ. Artois and CNRS. 
 * 
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

package constraints.global;

import static utility.Kit.control;

import java.util.stream.IntStream;

import org.xcsp.common.Types.TypeOperatorRel;
import org.xcsp.common.Utilities;

import constraints.ConstraintGlobal;
import interfaces.Tags.TagAC;
import interfaces.Tags.TagCallCompleteFiltering;
import interfaces.Tags.TagNotSymmetric;
import problem.Problem;
import propagation.AC;
import variables.Domain;
import variables.Variable;

/**
 * This constraint ensures that the tuple formed by the values assigned to a first list is less than (or equal to) the
 * tuple formed by the values assigned to a second list. The filtering algorithm is derived from "Propagation algorithms
 * for lexicographic ordering constraints", Artificial Intelligence, 170(10): 803-834 (2006) by Alan M. Frisch, Brahim
 * Hnich, Zeynep Kiziltan, Ian Miguel, and Toby Walsh. The code below is quite close to the one that can be found in
 * Chapter 12 of "Constraint Networks", ISTE/Wiely (2009) by C. Lecoutre.
 * 
 * @author Christophe Lecoutre
 */
public abstract class Lexicographic extends ConstraintGlobal implements TagAC, TagCallCompleteFiltering, TagNotSymmetric {

	public static Lexicographic buildFrom(Problem pb, Variable[] list1, Variable[] list2, TypeOperatorRel op) {
		switch (op) {
		case LT:
			return new LexicographicLT(pb, list1, list2);
		case LE:
			return new LexicographicLE(pb, list1, list2);
		case GE:
			return new LexicographicLE(pb, list2, list1);
		default: // GT
			return new LexicographicLT(pb, list2, list1);
		}
	}

	@Override
	public boolean isSatisfiedBy(int[] t) {
		for (int i = 0; i < half; i++) {
			int v = t[pos1[i]], w = t[pos2[i]];
			if (v < w)
				return true;
			if (v > w)
				return false;
		}
		return !strictOrdering;
	}

	/**
	 * A first list (actually array) of variables
	 */
	private final Variable[] list1;

	/**
	 * A second list (actually array) of variables
	 */
	private final Variable[] list2;

	/**
	 * pos1[i] is the position of the variable list1[i] in the constraint scope
	 */
	private final int[] pos1;

	/**
	 * pos2[i] is the position of the variable list2[i] in the constraint scope
	 */
	private final int[] pos2;

	/**
	 * This field indicates if the ordering between the two lists must be strictly respected; if true then we have to
	 * enforce <= (le), otherwise we have to enforce < (lt)
	 */
	private final boolean strictOrdering;

	/**
	 * The size of the lists (half of the scope size if no variable occurs several times)
	 */
	private final int half;

	/**
	 * A time counter used during filtering
	 */
	private int lex_time;

	/**
	 * lex_times[x] gives the time at which the variable (at position) x has been set (pseudo-assigned)
	 */
	private final int[] lex_times;

	/**
	 * lex_vals[x] gives the value of the variable (at position) x set at time lex_times[x]
	 */
	private final int[] lex_vals;

	/**
	 * Build a constraint Lexicographic for the specified problem over the two specified lists of variables
	 * 
	 * @param pb
	 *            the problem to which the constraint is attached
	 * @param list1
	 *            a first list of variables
	 * @param list2
	 *            a second list of variables
	 * @param strictOrdering
	 *            if true, the ordering between formed tuples must be strict
	 */
	public Lexicographic(Problem pb, Variable[] list1, Variable[] list2, boolean strictOrdering) {
		super(pb, pb.vars(list1, list2));
		this.half = list1.length;
		this.list1 = list1;
		this.list2 = list2;
		control(1 < half && half == list2.length);
		this.pos1 = IntStream.range(0, half).map(i -> Utilities.indexOf(list1[i], scp)).toArray();
		this.pos2 = IntStream.range(0, half).map(i -> Utilities.indexOf(list2[i], scp)).toArray();
		this.strictOrdering = strictOrdering;
		this.lex_times = new int[scp.length];
		this.lex_vals = new int[scp.length];
		defineKey(strictOrdering); // TODO adding the positions pos1 and pos2? (in case there are several occurrences of
									// the same variable)
	}

	private void set(int p, int v) {
		lex_times[p] = lex_time;
		lex_vals[p] = v;
	}

	private boolean isConsistentPair(int alpha, int v) {
		lex_time++;
		set(pos1[alpha], v);
		set(pos2[alpha], v);
		for (int i = alpha + 1; i < half; i++) {
			int x = pos1[i], y = pos2[i];
			int minx = lex_times[x] == lex_time ? lex_vals[x] : list1[i].dom.firstValue();
			int maxy = lex_times[y] == lex_time ? lex_vals[y] : list2[i].dom.lastValue();
			if (minx < maxy)
				return true;
			if (minx > maxy)
				return false;
			set(x, minx);
			set(y, maxy);
		}
		return !strictOrdering;
	}

	@Override
	public boolean runPropagator(Variable dummy) {
		int alpha = 0;
		while (alpha < half) {
			Domain dom1 = list1[alpha].dom, dom2 = list2[alpha].dom;
			if (AC.enforceLE(dom1, dom2) == false) // enforce (AC on) x <= y (list1[alpha] <= list2[alpha])
				return false;
			if (dom1.size() == 1 && dom2.size() == 1) {
				if (dom1.singleValue() < dom2.singleValue())
					return entailed();
				assert dom1.singleValue() == dom2.singleValue();
				alpha++;
			} else {
				int min1 = dom1.firstValue(), min2 = dom2.firstValue();
				assert min1 <= min2;
				if (min1 == min2 && !isConsistentPair(alpha, min1))
					if (dom2.removeValue(min2) == false)
						return false;
				int max1 = dom1.lastValue(), max2 = dom2.lastValue();
				assert max1 <= max2;
				if (max1 == max2 && !isConsistentPair(alpha, max1))
					if (dom1.removeValue(max1) == false)
						return false;
				assert dom1.firstValue() < dom2.lastValue();
				return true;
			}
		}
		assert alpha == half;
		return !strictOrdering;
	}

	// ************************************************************************
	// ***** Constraint LexicographicLT
	// ************************************************************************

	public static final class LexicographicLT extends Lexicographic {
		public LexicographicLT(Problem pb, Variable[] list1, Variable[] list2) {
			super(pb, list1, list2, true);
		}
	}

	// ************************************************************************
	// ***** Constraint LexicographicLE
	// ************************************************************************

	public static final class LexicographicLE extends Lexicographic {
		public LexicographicLE(Problem pb, Variable[] list1, Variable[] list2) {
			super(pb, list1, list2, false);
		}
	}
}
