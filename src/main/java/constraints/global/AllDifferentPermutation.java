/**
 * AbsCon - Copyright (c) 2017, CRIL-CNRS - lecoutre@cril.fr
 * 
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the CONTRAT DE LICENCE DE LOGICIEL LIBRE CeCILL which accompanies this
 * distribution, and is available at http://www.cecill.info
 */
package constraints.global;

import java.util.Arrays;

import interfaces.Observers.ObserverBacktracking.ObserverBacktrackingSystematic;
import interfaces.Tags.TagGACUnguaranteed;
import problem.Problem;
import sets.SetSparseReversible;
import utility.Kit;
import variables.Domain;
import variables.Variable;

public final class AllDifferentPermutation extends AllDifferentAbstract implements TagGACUnguaranteed, ObserverBacktrackingSystematic {

	private SetSparseReversible unfixedVars, unfixedIdxs;

	private Variable[] residues1, residues2;

	@Override
	public void restoreBefore(int depth) {
		unfixedVars.restoreLimitAtLevel(depth);
		unfixedIdxs.restoreLimitAtLevel(depth);
	}

	@Override
	public void afterProblemConstruction() {
		super.afterProblemConstruction();
		unfixedVars = new SetSparseReversible(scp.length, problem.variables.length + 1);
		unfixedIdxs = new SetSparseReversible(scp[0].dom.initSize(), problem.variables.length + 1);
	}

	private Variable findAnotherWatchedUnifxedVariable(int idx, Variable otherWatchedVariable) {
		int[] dense = unfixedVars.dense;
		for (int i = unfixedVars.limit; i >= 0; i--) {
			Variable var = scp[dense[i]];
			if (var != otherWatchedVariable && var.dom.present(idx))
				return var;
		}
		return null;
	}

	public AllDifferentPermutation(Problem pb, Variable[] scp) {
		super(pb, scp);
		Kit.control(Variable.isPermutationElligible(scp));
		residues1 = new Variable[scp[0].dom.initSize()];
		residues2 = new Variable[scp[0].dom.initSize()];
		Arrays.fill(residues1, scp[0]);
		Arrays.fill(residues2, scp[scp.length - 1]);
	}

	@Override
	public boolean runPropagator(Variable dummy) {
		int level = problem.solver.depth();
		int[] dense = unfixedVars.dense;
		for (int i = unfixedVars.limit; i >= 0; i--) {
			Variable x = scp[dense[i]];
			if (x.dom.size() == 1) {
				int a = x.dom.unique();
				unfixedVars.remove(dense[i], level);
				unfixedIdxs.remove(a, level);
				for (int j = unfixedVars.limit; j >= 0; j--) {
					Variable y = scp[dense[j]];
					Domain dy = y.dom;
					if (dy.present(a)) {
						if (!dy.remove(a))
							return false;
						if (dy.size() == 1) {
							// System.out.println("moving from " + i + " to " + (j+1));
							i = Math.max(i, j + 1); // +1 because i-- before a new iteration
						}
					}
				}
			}
			// else if (variable.domain.getCurrentSize() == 2) {
			// int first = variable.domain.getFirstValidIndex();
			// s += variable + "=(" + first + "," + variable.domain.getNextValidIndexAfter(first) + ") ";
			// cnt++;
			// }
		}

		dense = unfixedIdxs.dense;
		for (int i = unfixedIdxs.limit; i >= 0; i--) {
			int a = dense[i];
			if (!residues1[a].dom.present(a)) {
				Variable x = findAnotherWatchedUnifxedVariable(a, residues2[a]);
				if (x != null)
					residues1[a] = x;
				else {
					x = residues2[a];
					if (x.dom.reduceTo(a) == false)
						return false;
					unfixedVars.remove(positionOf(x), level);
					unfixedIdxs.remove(a, level);
				}
			}
			assert residues1[a].dom.size() > 1 : residues1[a] + " " + a + " " + residues1[a].dom.size();

			if (!residues2[a].dom.present(a)) {
				Variable x = findAnotherWatchedUnifxedVariable(a, residues1[a]);
				if (x != null)
					residues2[a] = x;
				else {
					x = residues1[a];
					x.dom.reduceTo(a);
					unfixedVars.remove(positionOf(x), level);
					unfixedIdxs.remove(a, level);
				}
			}
		}
		return true;
	}
}
