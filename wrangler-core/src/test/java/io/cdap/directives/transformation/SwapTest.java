/*
 *  Copyright © 2017-2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package io.cdap.directives.transformation;

import io.cdap.directives.column.Swap;
import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.RecipeException;
import io.cdap.wrangler.api.Row;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link Swap}
 */
public class SwapTest {

  @Test
  public void testSwap() throws Exception {
    String[] directives = new String[] {
      "swap a b",
    };

    List<Row> rows = Arrays.asList(
      new Row("a", 1).add("b", "sample string")
    );

    rows = TestingRig.execute(directives, rows);

    Assert.assertTrue(rows.size() == 1);
    Assert.assertEquals(1, rows.get(0).getValue("b"));
    Assert.assertEquals("sample string", rows.get(0).getValue("a"));
  }

  @Test(expected = RecipeException.class)
  public void testSwapFeildNotFound() throws Exception {
    String[] directives = new String[] {
      "swap a b",
    };

    List<Row> rows = Arrays.asList(
      new Row("a", 1).add("c", "sample string")
    );

    TestingRig.execute(directives, rows);
  }

}
