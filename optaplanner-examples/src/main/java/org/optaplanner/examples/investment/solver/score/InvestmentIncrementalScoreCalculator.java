/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.examples.investment.solver.score;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.impl.score.director.incremental.AbstractIncrementalScoreCalculator;
import org.optaplanner.examples.investment.domain.AssetClassAllocation;
import org.optaplanner.examples.investment.domain.InvestmentSolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvestmentIncrementalScoreCalculator extends AbstractIncrementalScoreCalculator<InvestmentSolution> {

    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

    private InvestmentSolution solution;

    private long squaredStandardDeviationFemtosMaximum;
    private long squaredStandardDeviationFemtos;

    private long hardScore;
    private long softScore;

    // ************************************************************************
    // Lifecycle methods
    // ************************************************************************

    public void resetWorkingSolution(InvestmentSolution solution) {
        this.solution = solution;
        squaredStandardDeviationFemtosMaximum = solution.getParametrization()
                .calculateSquaredStandardDeviationFemtosMaximum();
        squaredStandardDeviationFemtos = 0L;
        hardScore = 0L;
        softScore = 0L;
        for (AssetClassAllocation allocation : solution.getAssetClassAllocationList()) {
            insertQuantityMillis(allocation, true);
        }
    }

    public void beforeEntityAdded(Object entity) {
        // Do nothing
    }

    public void afterEntityAdded(Object entity) {
        insertQuantityMillis((AssetClassAllocation) entity, false);
    }

    public void beforeVariableChanged(Object entity, String variableName) {
        retractQuantityMillis((AssetClassAllocation) entity);
    }

    public void afterVariableChanged(Object entity, String variableName) {
        insertQuantityMillis((AssetClassAllocation) entity, false);
    }

    public void beforeEntityRemoved(Object entity) {
        retractQuantityMillis((AssetClassAllocation) entity);
    }

    public void afterEntityRemoved(Object entity) {
        // Do nothing
    }

    // ************************************************************************
    // Modify methods
    // ************************************************************************

    private void insertQuantityMillis(AssetClassAllocation allocation, boolean reset) {
        if (squaredStandardDeviationFemtos > squaredStandardDeviationFemtosMaximum) {
            hardScore += squaredStandardDeviationFemtos - squaredStandardDeviationFemtosMaximum;
        }
        squaredStandardDeviationFemtos += calculateStandardDeviationSquaredFemtosDelta(allocation, reset);
        if (squaredStandardDeviationFemtos > squaredStandardDeviationFemtosMaximum) {
            hardScore -= squaredStandardDeviationFemtos - squaredStandardDeviationFemtosMaximum;
        }
        softScore += allocation.getQuantifiedExpectedReturnMicros();
    }

    private void retractQuantityMillis(AssetClassAllocation allocation) {
        if (squaredStandardDeviationFemtos > squaredStandardDeviationFemtosMaximum) {
            hardScore += squaredStandardDeviationFemtos - squaredStandardDeviationFemtosMaximum;
        }
        squaredStandardDeviationFemtos -= calculateStandardDeviationSquaredFemtosDelta(allocation, false);
        if (squaredStandardDeviationFemtos > squaredStandardDeviationFemtosMaximum) {
            hardScore -= squaredStandardDeviationFemtos - squaredStandardDeviationFemtosMaximum;
        }
        softScore -= allocation.getQuantifiedExpectedReturnMicros();
    }

    private long calculateStandardDeviationSquaredFemtosDelta(AssetClassAllocation allocation, boolean reset) {
        long squaredFemtos = 0L;
        for (AssetClassAllocation other : solution.getAssetClassAllocationList()) {
            if (allocation == other) {
                long micros = allocation.getQuantifiedStandardDeviationRiskMicros();
                squaredFemtos += micros * micros * 1000L;
            } else {
                long picos = allocation.getQuantifiedStandardDeviationRiskMicros() * other.getQuantifiedStandardDeviationRiskMicros();
                squaredFemtos += picos * allocation.getAssetClass().getCorrelationMillisMap().get(other.getAssetClass());
                // TODO FIXME the reset hack only works if there are no moves that mix multiple before/after notifications
                if (!reset) {
                    squaredFemtos += picos * other.getAssetClass().getCorrelationMillisMap().get(allocation.getAssetClass());
                }
            }
        }
        return squaredFemtos;
    }

    @Override
    public Score calculateScore() {
        return HardSoftLongScore.valueOf(hardScore, softScore);
    }

}
