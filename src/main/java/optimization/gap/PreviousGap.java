package optimization.gap;

import optimization.Optimizer;
import problem.Problem;

public class PreviousGap extends GapStrategy{
    private boolean firstBound=true;
    private long lastBound;

    public PreviousGap(Optimizer optimizer, boolean minimization, Problem problem) {
        super(optimizer, minimization, problem);
    }

    @Override
    protected long reinit() {
        gap=1;
        return gap;
    }
    /**
     * Return the previous limit of objective constraint.
     * @return The previous limit
     */
    protected long previousLimit() {
        return (problem.optimizer.ctr.objectiveValue()+gap);
    }
    @Override
    protected long internalNextGap() {
        if(firstBound) {
            firstBound=false;
            lastBound = problem.solver.solutions.bestBound;
            return gap;
        }
        gap = (long) ((Math.abs(problem.solver.solutions.bestBound - lastBound)*problem.head.control.optimization.prevFactor) + 1);
        lastBound = problem.solver.solutions.bestBound;

        return gap;

    }


}

