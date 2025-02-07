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
package com.adobe.cq.commerce.core.components.internal.servlets;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.servlethelpers.MockRequestPathInfo;
import org.apache.sling.servlethelpers.MockSlingHttpServletRequest;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adobe.cq.commerce.core.components.client.MagentoGraphqlClient;
import com.adobe.cq.commerce.core.components.internal.services.MockUrlProviderConfiguration;
import com.adobe.cq.commerce.core.components.internal.services.UrlProviderImpl;
import com.adobe.cq.commerce.core.components.internal.services.urlformats.CategoryPageWithUrlKey;
import com.adobe.cq.commerce.core.components.internal.services.urlformats.CategoryPageWithUrlPath;
import com.adobe.cq.commerce.core.components.services.urls.UrlProvider;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.magento.graphql.CategoryTree;
import com.adobe.cq.commerce.magento.graphql.Query;
import com.adobe.cq.commerce.magento.graphql.gson.Error;
import com.day.cq.wcm.api.PageManagerFactory;
import com.shopify.graphql.support.ID;
import io.wcm.testing.mock.aem.junit.AemContext;
import io.wcm.testing.mock.aem.junit.AemContextCallback;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CategoryPageRedirectServletTest {

    @Rule
    public final AemContext context = createContext("/context/jcr-content-redirect-servlet.json");
    private final CategoryPageRedirectServlet servlet = new CategoryPageRedirectServlet();

    private MockSlingHttpServletRequest request;
    private MockRequestPathInfo mockRequestPathInfo;
    private MockSlingHttpServletResponse response;
    private UrlProviderImpl urlProvider;
    private MockUrlProviderConfiguration config;
    @Mock
    private MagentoGraphqlClient mockClient;

    private static AemContext createContext(String contentPath) {
        return new AemContext(
            (AemContextCallback) context -> {
                // Load page structure
                context.load().json(contentPath, "/content");
            },
            ResourceResolverType.JCR_MOCK);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        request = new MockSlingHttpServletRequest(context.resourceResolver()) {
            @Override
            protected MockRequestPathInfo newMockRequestPathInfo() {
                return mockRequestPathInfo = super.newMockRequestPathInfo();
            }
        };
        request.setResource(context.resourceResolver().getResource("/content/venia/us/en"));
        response = spy(new MockSlingHttpServletResponse());
        config = new MockUrlProviderConfiguration();
        urlProvider = new UrlProviderImpl();

        context.registerService(UrlProvider.class, urlProvider);
        context.registerService(PageManagerFactory.class, rr -> context.pageManager());
        context.registerInjectActivateService(servlet);
        context.registerAdapter(SlingHttpServletRequest.class, MagentoGraphqlClient.class, mockClient);
    }

    @Test
    public void testMissingCategorySuffix() throws IOException {
        mockRequestPathInfo.setSuffix(null);
        servlet.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing redirect suffix.");
    }

    @Test
    public void testWrongSuffixLength() throws IOException {
        mockRequestPathInfo.setSuffix("/some/wrong/suffix");
        servlet.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Redirect suffix has wrong format.");
    }

    @Test
    public void testNoCategoryPage() throws IOException {
        mockRequestPathInfo.setSuffix("/test_uid");
        // delete the category page
        context.resourceResolver().delete(context.resourceResolver().getResource("/content/venia/us/en/products/category-page"));
        context.resourceResolver().commit();
        servlet.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testNoCategory() throws IOException {
        mockRequestPathInfo.setSuffix("/test_uid");

        config.setCategoryPageUrlFormat(CategoryPageWithUrlKey.PATTERN);

        urlProvider.activate(config);

        Query mockQuery = mock(Query.class);
        GraphqlResponse<Query, Error> graphQlResponse = new GraphqlResponse<>();
        graphQlResponse.setData(mockQuery);

        when(mockQuery.getCategoryList()).thenReturn(Collections.emptyList());
        when(mockClient.execute(any())).thenReturn(graphQlResponse);

        servlet.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testUrlPathMatchingUrlProviderConfig() throws IOException {
        mockRequestPathInfo.setSelectorString(CategoryPageRedirectServlet.SELECTOR);
        mockRequestPathInfo.setSuffix("/test_uid");

        config.setCategoryPageUrlFormat(CategoryPageWithUrlPath.PATTERN);

        urlProvider.activate(config);

        Query mockQuery = mock(Query.class);
        CategoryTree categoryTree = mock(CategoryTree.class);
        ID uid = new ID("test_uid");

        when(categoryTree.getUid()).thenReturn(uid);
        when(categoryTree.getUrlKey()).thenReturn("test_url_key");
        when(categoryTree.getUrlPath()).thenReturn("test_url_path");
        when(mockQuery.getCategoryList()).thenReturn(Collections.singletonList(categoryTree));

        GraphqlResponse<Query, Error> graphQlResponse = new GraphqlResponse<>();
        graphQlResponse.setData(mockQuery);

        when(mockClient.execute(any())).thenReturn(graphQlResponse);

        servlet.doGet(request, response);
        verify(response).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        verify(response).setHeader("Location", "/content/venia/us/en/products/category-page.html/test_url_path.html");
    }

    @Test
    public void testUrlKeyMatchingUrlProviderConfig() throws IOException {
        mockRequestPathInfo.setSelectorString(CategoryPageRedirectServlet.SELECTOR);
        mockRequestPathInfo.setSuffix("/test_uid");

        config.setCategoryPageUrlFormat(CategoryPageWithUrlKey.PATTERN);

        urlProvider.activate(config);

        Query mockQuery = mock(Query.class);
        CategoryTree categoryTree = mock(CategoryTree.class);
        ID uid = new ID("test_uid");

        when(categoryTree.getUid()).thenReturn(uid);
        when(categoryTree.getUrlKey()).thenReturn("test_url_key");
        when(categoryTree.getUrlPath()).thenReturn("test_url_path");
        when(mockQuery.getCategoryList()).thenReturn(Collections.singletonList(categoryTree));

        GraphqlResponse<Query, Error> graphQlResponse = new GraphqlResponse<>();
        graphQlResponse.setData(mockQuery);

        when(mockClient.execute(any())).thenReturn(graphQlResponse);

        servlet.doGet(request, response);
        verify(response).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        verify(response).setHeader("Location", "/content/venia/us/en/products/category-page.html/test_url_key.html");
    }

}
