/**
 * AbsCon - Copyright (c) 2017, CRIL-CNRS - lecoutre@cril.fr
 * 
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the CONTRAT DE LICENCE DE LOGICIEL LIBRE CeCILL which accompanies this
 * distribution, and is available at http://www.cecill.info
 */
package search.statistics;

import static dashboard.Output.CPU;
import static dashboard.Output.MEM;
import static dashboard.Output.N_BUILT_BRANCHES;
import static dashboard.Output.N_EFFECTIVE_SINGLETON_TESTS;
import static dashboard.Output.N_FOUND_SINGLETONS;
import static dashboard.Output.N_SINGLETON_TESTS;
import static dashboard.Output.STOP;
import static dashboard.Output.SUM_BRANCH_SIZES;
import static dashboard.Output.WCK;

import java.text.NumberFormat;

import org.xcsp.common.Types.TypeFramework;

import dashboard.Output;
import interfaces.ObserverRuns;
import interfaces.ObserverSearch;
import learning.LearnerStatesEquivalence;
import learning.ReductionOperator;
import problem.ProblemStuff.MapAtt;
import propagation.GAC;
import propagation.Forward;
import propagation.SAC;
import propagation.SAC.SACGreedy;
import search.Solver;
import search.backtrack.SolverBacktrack;
import utility.Enums.EStopping;
import utility.Kit;
import utility.Kit.Stopwatch;
import variables.Variable;

/**
 * This class allows gathering all statistics of a given solver.
 */
public abstract class Statistics implements ObserverRuns, ObserverSearch {
	/*
	 * some statistics work is performed before and after performing preprocessing in order to take into account restarts and abstraction,
	 */
	@Override
	public final void beforePreprocessing() {
		stopwatch.start();
		if (solver instanceof SolverBacktrack && (((SolverBacktrack) solver).learnerNogoods != null))
			nPreproAddedNogoods = (((SolverBacktrack) solver).learnerNogoods).nNogoods;
		nPreproAddedCtrs = solver.problem.constraints.length;
	}

	@Override
	public final void afterPreprocessing() {
		preproWck += stopwatch.wckTime();
		if (solver instanceof SolverBacktrack && (((SolverBacktrack) solver).learnerNogoods != null))
			nPreproAddedNogoods = (((SolverBacktrack) solver).learnerNogoods).nNogoods - nPreproAddedNogoods;
		nPreproAddedCtrs = solver.problem.constraints.length - nPreproAddedCtrs;
		nPreproRemovedValues = Variable.nRemovedValuesFor(solver.problem.variables);
		nPreproRemovedTuples = solver.propagation.nTuplesRemoved;
		nPreproInconsistencies = solver.stopping == EStopping.FULL_EXPLORATION ? 1 : 0;
	}

	@Override
	public final void beforeSolving() {
		stopwatch.start();
	}

	@Override
	public final void afterSolving() {
		solvingWck += stopwatch.wckTime();
	}

	@Override
	public void afterRun() {
		searchWck = stopwatch.wckTime();
	}

	public void onAssignment(Variable x) {
		nNodes++;
		if (x.dom.size() > 1)
			nDecisions++;
		// nAssignments++; done elsewhere
	}

	public void onRefutation(Variable x) {
		if (x.dom.size() > 1) {
			nNodes++;
			nDecisions++;
		}
	}

	private static NumberFormat nformat = NumberFormat.getInstance();

	public final Solver solver;

	public final Stopwatch stopwatch = new Stopwatch();

	public long nNodes = 1, nDecisions, nWrongDecisions, nBacktracks, nAssignments, nFailedAssignments;

	public long nPreproRemovedValues, nPreproRemovedTuples, nPreproAddedCtrs, nPreproAddedNogoods, nPreproInconsistencies;

	public long solvingWck, preproWck, searchWck, firstSolWck, firstSolCpu, lastSolWck, lastSolCpu;

	private long tmpNbAssignments, tmpNbBacktracks, tmpNbFailedAssignments;

	public Statistics(Solver solver) {
		this.solver = solver;
	}

	public long numberSafe() {
		return nNodes + nAssignments + nBacktracks;
	}

	public void store() {
		tmpNbAssignments = nAssignments;
		tmpNbFailedAssignments = nFailedAssignments;
		tmpNbBacktracks = nBacktracks;
	}

	public void restore() {
		nAssignments = tmpNbAssignments;
		nFailedAssignments = tmpNbFailedAssignments;
		nBacktracks = tmpNbBacktracks;
	}

	public final long nEffectiveFilterings() {
		return solver.problem.stuff.nEffectiveFilterings;
	}

	public final long nRevisions() {
		return solver.propagation instanceof Forward ? ((Forward) solver.propagation).reviser.nRevisions : 0;
	}

	public final long nUselessRevisions() {
		return solver.propagation instanceof Forward ? ((Forward) solver.propagation).reviser.nUselessRevisions : 0;
	}

	public final long nSingletonTests() {
		return solver.propagation.nSingletonTests;
	}

	public final long nEffectiveSingletonTests() {
		return solver.propagation.nEffectiveSingletonTests;
	}

	public void manageSolution() {
		long cpu = solver.head.stopwatch.cpuTime(), wck = solver.head.instanceStopwatch.wckTime();
		if (solver.solManager.found == 1) {
			firstSolCpu = cpu;
			firstSolWck = wck;
		}
		lastSolCpu = cpu;
		lastSolWck = wck;
	}

	public final MapAtt preproAttributes() {
		MapAtt m = new MapAtt("Preprocessing");
		m.put("filters", nEffectiveFilterings());
		m.put("revisions", "(" + nRevisions() + ",useless=" + nUselessRevisions() + ")", nRevisions() > 0);
		if (solver.propagation instanceof GAC)
			m.put("nACremovedValues", ((GAC) (solver.propagation)).nPreproRemovals);
		m.put("nTotalRemovedValues", nPreproRemovedValues);
		m.put("inconsistency", nPreproInconsistencies > 0);
		m.separator();
		if (nPreproRemovedTuples > 0 || nPreproAddedNogoods > 0 || nPreproAddedCtrs > 0) {
			m.put("nRemovedTuples", nPreproRemovedTuples);
			m.put("nNogoods", nPreproAddedNogoods);
			m.put("nAddedCtrs", nPreproAddedCtrs);
			m.separator();
		}
		if (nSingletonTests() > 0) {
			m.put(N_SINGLETON_TESTS, nSingletonTests());
			m.put(N_EFFECTIVE_SINGLETON_TESTS, nEffectiveSingletonTests());
			if (solver.propagation instanceof SAC)
				m.put(N_FOUND_SINGLETONS, ((SAC) (solver.propagation)).nFoundSingletons);
			if (solver.propagation instanceof SACGreedy) {
				m.put(N_BUILT_BRANCHES, ((SACGreedy) (solver.propagation)).nBranchesBuilt);
				m.put(SUM_BRANCH_SIZES, ((SACGreedy) (solver.propagation)).sumBranchSizes);
			}
			m.separator();
		}
		if (solver.solManager.found > 0) {
			m.put("foundSolutions", solver.solManager.found);
			m.put("firstSolCpu", firstSolCpu / 1000.0);
			m.separator();
		}
		m.put(WCK, preproWck / 1000.0);
		m.put(CPU, solver.head.stopwatch.cpuTimeInSeconds());

		m.put(MEM, Kit.memoryInMb());
		return m;
	}

	public abstract MapAtt runAttributes();

	public abstract MapAtt cumulatedAttributes();

	// private boolean statMouny = true;

	public MapAtt globalAttributes() {
		MapAtt m = cumulatedAttributes();
		// m.put(EXPIRED_TIME, solver.rs.isTimeExpiredForCurrentInstance());
		// m.put(TOTAL_EXPLORATION, solver.isFullExploration());
		m.put(STOP, solver.stopping == null ? "no" : solver.stopping.toString());
		m.put("wrong", solver.stats.nWrongDecisions);
		// if (statMouny && solver.propagation instanceof ACPartial) {
		// int[] t = ((ACPartial) solver.propagation).statistics;
		// map.put("nbWOs", t[0] + ""); map.put("nbFPs", t[1] + ""); map.put("avgWOs", t[2] + ""); map.put("avgFPs", t[3] + ""); }
		if (solver.solManager.found > 0) {
			if (solver.problem.settings.framework != TypeFramework.CSP) {
				m.put("bestBound", solver.solManager.bestBound);
				m.put("bestBoundWck", lastSolWck / 1000.0);
				m.put("bestBoundCpu", lastSolCpu / 1000.0);
			}
			m.put("foundSolutions", solver.solManager.found);
			m.put("firstSolCpu", firstSolCpu / 1000.0);
			m.separator();
		}
		m.put(WCK, solver.head.instanceStopwatch.wckTimeInSeconds());
		m.put(CPU, solver.head.stopwatch.cpuTimeInSeconds());
		m.put(MEM, Kit.memoryInMb());
		return m;
	}

	// ************************************************************************
	// ***** StatisticsBacktrack
	// ************************************************************************

	public static final class StatisticsBacktrack extends Statistics {

		protected SolverBacktrack solver;

		public StatisticsBacktrack(SolverBacktrack solver) {
			super(solver);
			this.solver = solver;
		}

		@Override
		public MapAtt runAttributes() {
			MapAtt m = new MapAtt("Run");
			if (solver.head.control.settingXml.competitionMode) {
				m.put("run", solver.restarter.numRun);
				m.put("dpt", solver.minDepth + ".." + solver.maxDepth);
				m.put("eff", nEffectiveFilterings());
				m.put("wrg", nWrongDecisions);
				if (Kit.memory() > 10000000000L)
					m.put(Output.MEM, Kit.memoryInMb());
				m.put(Output.WCK, stopwatch.wckTimeInSeconds());
				if (solver.learnerNogoods != null)
					m.putPositive("ngd", solver.learnerNogoods.nNogoods);
				if (solver.solManager.found > 0) {
					if (solver.problem.settings.framework == TypeFramework.CSP)
						m.put("nSols", solver.solManager.found);
					else
						m.put("bnd", nformat.format(solver.solManager.bestBound));
				}
				return m;
			}

			m.put("num", solver.restarter.numRun);
			m.put(Output.DEPTH, solver.minDepth + ".." + solver.maxDepth);
			m.put("filters", nEffectiveFilterings());
			m.put("revisions", "(" + nRevisions() + ",useless=" + nUselessRevisions() + ")", nRevisions() > 0);
			if (nSingletonTests() > 0) { // solver.getPreproPropagationTechnique() instanceof SingletonArcConsistency) {
				m.put(Output.N_SINGLETON_TESTS, nSingletonTests());
				m.put(Output.N_EFFECTIVE_SINGLETON_TESTS, nEffectiveSingletonTests());
			}
			m.put(Output.WCK, stopwatch.wckTimeInSeconds());
			if (Kit.memory() > 10000000000L)
				m.put(Output.MEM, Kit.memoryInMb());
			m.separator();

			// if (solver.propagation instanceof AC) {
			// FailedValueBasedConsistency fvbc = ((AC) solver.propagation).fvbc;
			// if (fvbc != null && fvbc.nInferredBacktracks > 0)
			// m.put(Output.N_INFERRED_BACKTRACKS, fvbc.nInferredBacktracks);
			// if (fvbc != null && fvbc.nInferredRemovals > 0)
			// m.put(Output.N_INFERRED_REMOVALS, fvbc.nInferredRemovals);
			// }
			m.put("decisions", nDecisions);
			m.put("wrong", nWrongDecisions);
			m.put("backtracks", nBacktracks);
			// m.put(Output.N_ASSIGNMENTS, nAssignments);
			m.put("failedAssignments", nFailedAssignments);
			// m.put(Output.N_VISITED_NODES, nVisitedNodes);
			if (solver.learnerNogoods != null)
				m.putPositive("nogoods", solver.learnerNogoods.nNogoods);
			if (solver.solManager.found > 0) {
				m.put("foundSolutions", solver.solManager.found);
				if (solver.problem.settings.framework != TypeFramework.CSP)
					m.put(Output.BEST_BOUND, solver.solManager.bestBound);
			}
			m.separator();
			if (solver.problem.stuff.nFilterCallsSTR > 0) {
				m.put(Output.N_FILTER_CALLS, solver.problem.stuff.nFilterCallsSTR);
				m.put(Output.AVG_TABLE_PROPORTION, (int) ((solver.problem.stuff.sumTableProportionsSTR / solver.problem.stuff.nFilterCallsSTR) * 100));
				m.put(Output.AVG_TABLE_SIZE, (int) (solver.problem.stuff.sumTableSizesSTR / solver.problem.stuff.nFilterCallsSTR));
				m.separator();
			}
			if (solver.learnerStates != null && solver.learnerStates instanceof LearnerStatesEquivalence && !solver.learnerStates.stopped) {
				LearnerStatesEquivalence learner = (LearnerStatesEquivalence) solver.learnerStates;
				m.put(Output.MAP_SIZE, learner.getMapSize());
				m.put(Output.N_INFERENCES, learner.nInferences);
				// map.put("nbInferredSolutions", solutionCounter.nbInferredSolutions );
				m.put(Output.N_TOO_LARGE_KEYS, learner.nbTooLargeKeys);
				m.separator();
			}
			if (solver.learnerStates != null) {
				ReductionOperator ro = solver.learnerStates.reductionOperator;
				// DecimalFormat df = new DecimalFormat("###.##",new DecimalFormatSymbols(Locale.ENGLISH));
				m.put(Output.N_SELIMINABLES, Kit.decimalFormat.format(ro.getProportionOfNbSEliminableVariables()));
				m.put(Output.N_RELIMINABLES, Kit.decimalFormat.format(ro.getProportionOfNbREliminableVariables()));
				m.put(Output.N_IELIMINABLES, Kit.decimalFormat.format(ro.getProportionOfNbIEliminableVariables()));
				m.put(Output.N_DELIMINABLES, Kit.decimalFormat.format(ro.getProportionOfNbDEliminableVariables()));
				m.put(Output.N_PELIMINABLES, Kit.decimalFormat.format(ro.getProportionOfNbPEliminableVariables()));
				m.separator();
			}
			return m;
		}

		@Override
		public MapAtt cumulatedAttributes() {
			MapAtt m = new MapAtt("Global");
			m.put("filters", nEffectiveFilterings());
			m.put("revisions", "(" + nRevisions() + ",useless=" + nUselessRevisions() + ")", nRevisions() > 0);
			if (nSingletonTests() > 0) { // solver.getPreproPropagationTechnique() instanceof SingletonArcConsistency) {
				m.put(Output.N_SINGLETON_TESTS, nSingletonTests());
				m.put(Output.N_EFFECTIVE_SINGLETON_TESTS, nEffectiveSingletonTests());
			}

			if (solver.learnerNogoods != null)
				m.putPositive("nogoods", solver.learnerNogoods.nNogoods);
			m.separator();
			return m;
		}
	}

	// ************************************************************************
	// ***** StatisticsLocal
	// ************************************************************************

	/**
	 * This class allows gathering all statistics of a given solver.
	 */
	public static final class StatisticsLocal extends Statistics {

		public StatisticsLocal(Solver solver) {
			super(solver);
		}

		@Override
		public MapAtt runAttributes() {
			MapAtt m = new MapAtt("Run");
			m.put("number", (solver.restarter.numRun == -1 ? "all" : solver.restarter.numRun));
			m.put(Output.N_ASSIGNMENTS, nAssignments);
			m.put(Output.WCK, searchWck / 1000.0);
			m.put(Output.CPU, solver.head.stopwatch.cpuTimeInSeconds());
			m.put(Output.MEM, Kit.memoryInMb());
			return m;
		}

		@Override
		public MapAtt cumulatedAttributes() {
			MapAtt m = new MapAtt("Global");
			m.put(Output.N_ASSIGNMENTS, nAssignments);
			return m;
		}
	}

}