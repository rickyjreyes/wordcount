package org.wordcountlab.experimental;

import org.junit.jupiter.api.Test;
import org.wordcountlab.Frequency;
import org.wordcountlab.Sorts;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests correctness and branch disclosure, NOT speed or selection quality. */
class AdaptiveSelectorTest {
    static ResearchSorts.Result checked(Frequency[] source) {
        Frequency[] original=source.clone(),expected=source.clone();
        Arrays.sort(expected,Sorts.ORDER);
        ResearchSorts.Result actual=ResearchSorts.sort(source,"adaptive-v1");
        assertArrayEquals(expected,actual.values());
        assertArrayEquals(original,source,"input must remain unchanged");
        assertTrue(actual.implementation().startsWith("adaptive-v1->"));
        return actual;
    }
    @Test void emptySingletonSortedAndStrictReverse() {
        assertEquals("adaptive-v1->trivial",checked(new Frequency[0]).implementation());
        assertEquals("adaptive-v1->trivial",
            checked(new Frequency[]{new Frequency("a",1)}).implementation());
        Frequency[] ascending=ExperimentLab.generated("sorted",2048,2017);
        assertEquals("adaptive-v1->already-ordered",checked(ascending).implementation());
        Frequency[] descending=ExperimentLab.generated("reverse",2048,2017);
        // Duplicate count-word keys do not occur in the generator; reverse strictly under total comparator.
        assertEquals("adaptive-v1->strict-reverse",checked(descending).implementation());
    }
    @Test void equalKeyIdentitiesDoNotTakeUnsafeReverseShortcut() {
        Frequency a=new Frequency("a",2),a2=new Frequency("a",2);
        Frequency[] data={new Frequency("b",1),a,a2};
        assertEquals("adaptive-v1->merge",checked(data).implementation());
    }
    @Test void explicitBucketAndSparseRoutes() {
        var dense=checked(ExperimentLab.generated("random",8192,2017));
        assertEquals("adaptive-v1->freqbucket->histogram+lexical-tie-sort",dense.implementation());
        var sparse=checked(ExperimentLab.generated("sparse",8192,2017));
        assertEquals("adaptive-v1->merge",sparse.implementation());
        var small=checked(ExperimentLab.generated("random",128,2017));
        assertEquals("adaptive-v1->merge",small.implementation());
    }
    @Test void holdoutFamilyCorrectnessForMultipleUnseenSeedsWithoutTimingClaims() {
        for(String shape:List.of("staircase","alternating","clusters","zipf","prefix","unicode",
                "nearly","runs","equal","few-counts","random","sparse"))
            for(int n:new int[]{0,1,2,17,127,512,2048})
                for(int seed:new int[]{101,4099,65537})checked(ExperimentLab.generated(shape,n,seed));
    }
}
