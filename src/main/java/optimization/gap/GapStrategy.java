package optimization.gap;

import optimization.Optimizer;
import problem.Problem;
import solver.Restarter;


public abstract class GapStrategy implements IGap {

    protected boolean minimization;
    protected Problem problem;
    protected Optimizer optimizer;

    private long highestUnsafeBestBound;

    protected long gap = 1;
    public boolean alwaysSafe = true;

    protected boolean isContinuous;
    private long previousResult=1;

    public GapStrategy(Optimizer optimizer, boolean minimization, Problem problem) {
        this.optimizer = optimizer;
        this.minimization = minimization;
        this.problem = problem;
        this.highestUnsafeBestBound = this.minimization ? Long.MAX_VALUE : Long.MIN_VALUE;
    }

    public boolean hasBeenAlwaysSafe() {
        return this.alwaysSafe;
    }


    public boolean isContinuous() {
        return gap == 1;
    }

    public void afterRun() {
        this.previousResult = 1;
        this.reinit();
    }

    protected abstract long reinit();

    @Override
    public long nextGap() {
        long min, max;
        if (minimization) {
            max = problem.solver.solutions.bestBound;
            min = optimizer.minBound;
        } else {
            min = problem.solver.solutions.bestBound;
            max = optimizer.maxBound;
        }

        this.checkBoundAndBudget();

        long result = internalNextGap();

        double ratio = ((double) result / this.previousResult);

        if (result + min >= max || this.problem.solver.solutions.lastBudget * ratio >= this.remainingBudget()) {
            result = this.reinit();
        }

        this.previousResult = result;

        this.highestUnsafeBestBound = this.minimization ? Math.min(this.highestUnsafeBestBound, max - result) : Math.max(this.highestUnsafeBestBound, min + result);

        this.alwaysSafe = this.alwaysSafe && this.isContinuous();

        System.out.println("this.alwaysSafe=" + this.alwaysSafe);
        System.out.println("gapNext=" + result);

        return minimization ? -result : result;
    }

    private long remainingBudget() {
        Restarter res = this.problem.solver.restarter;
        return res.currCutoff - res.measureSupplier.get();
    }

    private void checkBoundAndBudget() {
        this.alwaysSafe = this.minimization ? this.highestUnsafeBestBound >= this.problem.solver.solutions.bestBound : this.highestUnsafeBestBound <= this.problem.solver.solutions.bestBound;
    }

    protected abstract long internalNextGap();
}

