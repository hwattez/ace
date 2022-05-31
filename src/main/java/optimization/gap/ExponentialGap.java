package optimization.gap;

import optimization.Optimizer;
import problem.Problem;

public class ExponentialGap extends GapStrategy {

    private final double factor;

    public ExponentialGap(Optimizer optimizer, boolean minimization, Problem problem) {
        super(optimizer, minimization, problem);
        this.factor = this.problem.head.control.optimization.expoGapFactor;
    }

    @Override
    protected long reinit() {
        this.gap = 1;
        return this.gap;
    }

    @Override
    protected long internalNextGap() {
        gap = (long) (gap * factor) + 1;
        return gap;
    }

}

