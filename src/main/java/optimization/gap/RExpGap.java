package optimization.gap;

import optimization.Optimizer;
import problem.Problem;

public class RExpGap extends GapStrategy {
    private long inner=1;
    private long outer=1;

    public RExpGap(Optimizer optimizer, boolean minimization, Problem problem) {
        super(optimizer, minimization, problem);
    }

    @Override
    protected long reinit() {
        this.inner=1;
        this.outer=1;
        return this.gap = this.inner;
    }

    @Override
    protected long internalNextGap() {
        if(this.inner>this.outer) {
            this.outer<<=1;
            this.inner=1;

        }else {
            this.inner<<=1;
        }

        return this.gap = this.inner;
    }

}


