/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobStatusMapperTest {
  @Test
  void mapsCompleteAndFailedJobConditions() {
    Job complete = new Job();
    JobCondition completeCondition = new JobCondition();
    completeCondition.setType("Complete");
    completeCondition.setStatus("True");
    completeCondition.setReason("Completed");
    completeCondition.setMessage("done");
    complete.setStatus(new JobStatus(null, null, null,
        java.util.List.of(completeCondition),
        null, null, 1, null, null, null, null));
    assertEquals("SUCCEEDED", JobStatusMapper.observe(complete).phase());

    Job failed = new Job();
    JobCondition failedCondition = new JobCondition();
    failedCondition.setType("Failed");
    failedCondition.setStatus("True");
    failedCondition.setReason("Error");
    failedCondition.setMessage("scanner failed");
    failed.setStatus(new JobStatus(null, null, null,
        java.util.List.of(failedCondition),
        null, null, 0, null, 1, null, null));
    assertEquals("FAILED", JobStatusMapper.observe(failed).phase());
  }

  @Test
  void mapsActiveJobToRunning() {
    Job active = new Job();
    active.setStatus(new JobStatus(1, null, null, null, null, null, null, null, null, null, null));
    assertEquals("RUNNING", JobStatusMapper.observe(active).phase());
  }
}
