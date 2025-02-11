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
package com.adobe.cq.commerce.core.components.services.sitemap;

import org.osgi.annotation.versioning.ConsumerType;

import com.adobe.cq.commerce.magento.graphql.ProductInterface;
import com.day.cq.wcm.api.Page;

/**
 * A service interface consumers may implement to provide a filter for products to include in the product sitemap.
 */
@ConsumerType
public interface SitemapProductFilter {

    /**
     * Implementations may return {@code true} when the given {@link ProductInterface} should be included in the product sitemap,
     * {@code false} otherwise.
     *
     * @param productPage the product {@link Page} giving the context in which the filter is called
     * @param product the {@link ProductInterface} to check for eligibility to be included in the product sitemap
     * @return {@code true} to include the product in the sitemap at the given product {@link Page}, {@code false} otherwise
     */
    boolean shouldInclude(Page productPage, ProductInterface product);
}
