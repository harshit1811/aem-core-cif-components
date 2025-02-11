/*******************************************************************************
 *
 *    Copyright 2021 Adobe. All rights reserved.
 *    This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License. You may obtain a copy
 *    of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under
 *    the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *    OF ANY KIND, either express or implied. See the License for the specific language
 *    governing permissions and limitations under the License.
 *
 ******************************************************************************/
package com.adobe.cq.commerce.extensions.recommendations.internal.models.v1.common;

import org.junit.Test;

import com.adobe.cq.commerce.extensions.recommendations.models.common.PriceRange;

import static org.junit.Assert.assertEquals;

public class PriceRangeImplTest {

    @Test
    public void testGetters() {
        PriceRange priceRange = new PriceRangeImpl(10.0, 12.0);
        assertEquals(Double.valueOf(10), priceRange.getMinPrice());
        assertEquals(Double.valueOf(12), priceRange.getMaxPrice());
    }

}
