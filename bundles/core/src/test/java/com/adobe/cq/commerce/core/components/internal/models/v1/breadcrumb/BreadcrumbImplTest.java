/*******************************************************************************
 *
 *    Copyright 2020 Adobe. All rights reserved.
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

package com.adobe.cq.commerce.core.components.internal.models.v1.breadcrumb;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.caconfig.ConfigurationBuilder;
import org.apache.sling.servlethelpers.MockRequestPathInfo;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.xss.XSSAPI;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import com.adobe.cq.commerce.core.MockLaunch;
import com.adobe.cq.commerce.core.components.internal.services.MockUrlProviderConfiguration;
import com.adobe.cq.commerce.core.components.internal.services.UrlProviderImpl;
import com.adobe.cq.commerce.core.components.services.ComponentsConfiguration;
import com.adobe.cq.commerce.core.components.services.urls.UrlProvider;
import com.adobe.cq.commerce.core.components.testing.Utils;
import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.client.GraphqlClientConfiguration;
import com.adobe.cq.commerce.graphql.client.HttpMethod;
import com.adobe.cq.commerce.graphql.client.impl.GraphqlClientImpl;
import com.adobe.cq.commerce.magento.graphql.CategoryInterface;
import com.adobe.cq.commerce.magento.graphql.CategoryTree;
import com.adobe.cq.commerce.magento.graphql.gson.QueryDeserializer;
import com.adobe.cq.launches.api.Launch;
import com.adobe.cq.sightly.SightlyWCMMode;
import com.adobe.cq.wcm.core.components.models.NavigationItem;
import com.adobe.cq.wcm.core.components.services.link.PathProcessor;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.WCMMode;
import com.day.cq.wcm.api.designer.Style;
import com.day.cq.wcm.scripting.WCMBindingsConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.shopify.graphql.support.ID;
import io.wcm.testing.mock.aem.junit.AemContext;
import io.wcm.testing.mock.aem.junit.AemContextCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BreadcrumbImplTest {

    private static final ValueMap MOCK_CONFIGURATION = new ValueMapDecorator(ImmutableMap.of("cq:graphqlClient", "default", "magentoStore",
        "my-store", "enableUIDSupport", "true"));

    private static final ComponentsConfiguration MOCK_CONFIGURATION_OBJECT = new ComponentsConfiguration(MOCK_CONFIGURATION);

    @Rule
    public final AemContext context = createContext("/context/jcr-content-breadcrumb.json");

    private static AemContext createContext(String contentPath) {
        return new AemContext(
            (AemContextCallback) context -> {
                // Load page structure
                context.load().json(contentPath, "/content");

                UrlProviderImpl urlProvider = new UrlProviderImpl();
                urlProvider.activate(new MockUrlProviderConfiguration());
                context.registerService(UrlProvider.class, urlProvider);

                context.registerAdapter(Resource.class, ComponentsConfiguration.class, MOCK_CONFIGURATION_OBJECT);

                ConfigurationBuilder mockConfigBuilder = Mockito.mock(ConfigurationBuilder.class);
                Utils.addDataLayerConfig(mockConfigBuilder, true);
                context.registerAdapter(Resource.class, ConfigurationBuilder.class, mockConfigBuilder);
            },
            ResourceResolverType.JCR_MOCK);
    }

    private static final String BREADCRUMB_RELATIVE_PATH = "/jcr:content/root/responsivegrid/breadcrumb";

    private HttpClient httpClient;
    private GraphqlClient graphqlClient;
    private Resource breadcrumbResource;
    private BreadcrumbImpl breadcrumbModel;

    @Before
    public void setUp() throws Exception {
        httpClient = mock(HttpClient.class);

        GraphqlClientConfiguration graphqlClientConfiguration = mock(GraphqlClientConfiguration.class);
        when(graphqlClientConfiguration.httpMethod()).thenReturn(HttpMethod.POST);

        graphqlClient = Mockito.spy(new GraphqlClientImpl());
        Whitebox.setInternalState(graphqlClient, "gson", QueryDeserializer.getGson());
        Whitebox.setInternalState(graphqlClient, "client", httpClient);
        Whitebox.setInternalState(graphqlClient, "configuration", graphqlClientConfiguration);

        context.registerAdapter(Resource.class, GraphqlClient.class, (Function<Resource, GraphqlClient>) input -> input.getValueMap().get(
            "cq:graphqlClient", String.class) != null ? graphqlClient : null);

        Utils.setupHttpResponse("graphql/magento-graphql-product-result.json", httpClient, HttpStatus.SC_OK, "{products(filter:{url_key");
        Utils.setupHttpResponse("graphql/magento-graphql-product-result.json", httpClient, HttpStatus.SC_OK, "{products(filter:{sku");

        // This sets the page attribute injected in the models with @Inject or @ScriptVariable
        SlingBindings slingBindings = (SlingBindings) context.request().getAttribute(SlingBindings.class.getName());

        XSSAPI xssApi = mock(XSSAPI.class);
        when(xssApi.filterHTML(Mockito.anyString())).then(i -> i.getArgumentAt(0, String.class));
        slingBindings.put("xssApi", xssApi);

        Style style = mock(Style.class);
        when(style.get(Mockito.anyString(), Mockito.anyInt())).then(i -> i.getArgumentAt(1, Object.class));
        slingBindings.put("currentStyle", style);

        SightlyWCMMode wcmMode = mock(SightlyWCMMode.class);
        when(wcmMode.isDisabled()).thenReturn(false);
        slingBindings.put("wcmmode", wcmMode);
        context.registerService(PathProcessor.class, new Utils.MockPathProcessor());
    }

    public void prepareModel(String pagePath) throws Exception {
        Page page = context.currentPage(pagePath);
        context.currentResource(pagePath + BREADCRUMB_RELATIVE_PATH);
        breadcrumbResource = Mockito.spy(context.resourceResolver().getResource(pagePath + BREADCRUMB_RELATIVE_PATH));
        when(breadcrumbResource.adaptTo(ComponentsConfiguration.class)).thenReturn(MOCK_CONFIGURATION_OBJECT);

        SlingBindings slingBindings = (SlingBindings) context.request().getAttribute(SlingBindings.class.getName());
        slingBindings.setResource(breadcrumbResource);
        slingBindings.put(WCMBindingsConstants.NAME_CURRENT_PAGE, page);
        slingBindings.put(WCMBindingsConstants.NAME_PROPERTIES, breadcrumbResource.getValueMap());
    }

    @Test
    public void testContentPages() throws Exception {
        prepareModel("/content/venia/us/en/another-page");
        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        Collection<NavigationItem> items = breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en", "another-page");
    }

    @Test
    public void testProductPage() throws Exception {
        Utils.setupHttpResponse("graphql/magento-graphql-product-result.json", httpClient, HttpStatus.SC_OK, "{products(filter:{url_key");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient, HttpStatus.SC_OK,
            "{products(filter:{sku");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient, HttpStatus.SC_OK,
            "breadcrumbs{category_uid,category_url_path,category_name}");
        prepareModel("/content/venia/us/en/products/product-page");

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSuffix("/tiberius-gym-tank.html");

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        List<NavigationItem> items = (List<NavigationItem>) breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en", "Men", "Tops", "Tiberius Gym Tank");

        NavigationItem menCategory = items.get(1);
        assertThat(menCategory.getURL()).isEqualTo("/content/venia/us/en/products/category-page.html/men.html");
        assertThat(menCategory.isActive()).isFalse();

        NavigationItem product = items.get(3);
        assertThat(product.getURL()).isEqualTo("/content/venia/us/en/products/product-page.html/tiberius-gym-tank.html");
        assertThat(product.isActive()).isTrue();
    }

    @Test
    public void testProductSpecificPage() throws Exception {
        Utils.setupHttpResponse("graphql/magento-graphql-product-result.json", httpClient, HttpStatus.SC_OK, "{products(filter:{url_key");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient,
            HttpStatus.SC_OK, "{products(filter:{sku");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient,
            HttpStatus.SC_OK, "breadcrumbs{category_uid,category_url_path,category_name}");
        prepareModel("/content/venia/us/en/products/product-page/product-specific-page");

        // We set the EDIT mode to see the page specific URL
        context.request().setAttribute(WCMMode.class.getName(), WCMMode.EDIT);

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSuffix("/tiberius-gym-tank.html");

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        List<NavigationItem> items = (List<NavigationItem>) breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en", "Men", "Tops", "Tiberius Gym Tank");

        NavigationItem product = items.get(3);
        assertThat(product.getURL()).isEqualTo(
            "/content/venia/us/en/products/product-page/product-specific-page.html/tiberius-gym-tank.html");
        assertThat(product.isActive()).isTrue();
    }

    @Test
    public void testProductSpecificPageOnLaunch() throws Exception {
        context.registerAdapter(Resource.class, Launch.class, (Function<Resource, Launch>) resource -> new MockLaunch(resource));
        context.request().setContextPath("");

        Utils.setupHttpResponse("graphql/magento-graphql-product-result.json", httpClient, HttpStatus.SC_OK, "{products(filter:{url_key");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient,
            HttpStatus.SC_OK, "{products(filter:{sku");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient,
            HttpStatus.SC_OK, "breadcrumbs{category_uid,category_url_path,category_name}");

        String launchPage = "/content/launches/2020/09/14/mylaunch/content/venia/us/en/products/product-page/product-specific-page";
        prepareModel(launchPage);

        // We set the EDIT mode to see the page specific URL
        context.request().setAttribute(WCMMode.class.getName(), WCMMode.EDIT);

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSuffix("/tiberius-gym-tank.html");

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        List<NavigationItem> items = (List<NavigationItem>) breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en", "Men", "Tops", "Tiberius Gym Tank");

        // Check that all the paths are pointing to pages in the Launch
        for (NavigationItem item : items) {
            assertThat(item.getURL()).startsWith("/content/launches/2020/09/14/mylaunch/content/venia");
        }

        NavigationItem product = items.get(3);
        assertThat(product.getURL()).isEqualTo(launchPage + ".html/tiberius-gym-tank.html");
        assertThat(product.isActive()).isTrue();
    }

    @Test
    public void testCategoryPage() throws Exception {
        Utils.setupHttpResponse("graphql/magento-graphql-category-uid.json", httpClient, HttpStatus.SC_OK,
            "{categoryList(filters:{url_key");
        Utils.setupHttpResponse("graphql/magento-graphql-category-breadcrumb-result.json", httpClient, HttpStatus.SC_OK,
            "{categoryList(filters:{category_uid");
        prepareModel("/content/venia/us/en/products/category-page");

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSuffix("/men.html");

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        List<NavigationItem> items = (List<NavigationItem>) breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en", "Men", "Tops");

        NavigationItem menCategory = items.get(1);
        assertThat(menCategory.getURL()).isEqualTo("/content/venia/us/en/products/category-page.html/men.html");
        assertThat(menCategory.isActive()).isFalse();

        NavigationItem topsCategory = items.get(2);
        assertThat(topsCategory.getURL()).isEqualTo("/content/venia/us/en/products/category-page.html/men/tops-men.html");
        assertThat(topsCategory.isActive()).isTrue();
    }

    @Test
    public void testCategorySpecificPage() throws Exception {
        Utils.setupHttpResponse("graphql/magento-graphql-category-uid.json", httpClient, HttpStatus.SC_OK,
            "{categoryList(filters:{url_key");
        Utils.setupHttpResponse("graphql/magento-graphql-category-breadcrumb-result.json", httpClient, HttpStatus.SC_OK,
            "{categoryList(filters:{category_uid");
        prepareModel("/content/venia/us/en/products/category-page/category-specific-page");

        // We set the EDIT mode to see the page specific URL
        context.request().setAttribute(WCMMode.class.getName(), WCMMode.EDIT);

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSuffix("/men.html");

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        List<NavigationItem> items = (List<NavigationItem>) breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en", "Men", "Tops");

        NavigationItem product = items.get(2);
        assertThat(product.getURL()).isEqualTo("/content/venia/us/en/products/category-page/category-specific-page.html/men/tops-men.html");
        assertThat(product.isActive()).isTrue();
    }

    @Test
    public void testProductPageWithCatalogPage() throws Exception {
        Utils.setupHttpResponse("graphql/magento-graphql-product-result.json", httpClient, HttpStatus.SC_OK, "{products(filter:{url_key");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient,
            HttpStatus.SC_OK, "{products(filter:{sku");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient,
            HttpStatus.SC_OK, "breadcrumbs{category_uid,category_url_path,category_name}");

        prepareModel("/content/venia/us/en/products/product-page");

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSuffix("/tiberius-gym-tank.html");

        // We change the "showMainCategories" property to false to include the catalog page in the breadcrumb
        Resource catalogPageResource = context.resourceResolver().getResource("/content/venia/us/en/products/jcr:content");
        ModifiableValueMap properties = catalogPageResource.adaptTo(ModifiableValueMap.class);
        properties.replace("showMainCategories", false);

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        Collection<NavigationItem> items = breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en", "products", "Men", "Tops", "Tiberius Gym Tank");
    }

    @Test
    public void testProductPageWithoutIdentifier() throws Exception {
        prepareModel("/content/venia/us/en/products/product-page");

        // In AEM editor, there isn't any selector in the page URL
        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        List<NavigationItem> items = (List<NavigationItem>) breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en");
    }

    @Test
    public void testCategoryPageWithoutIdentifier() throws Exception {
        prepareModel("/content/venia/us/en/products/category-page");

        // In AEM editor, there isn't any selector in the page URL
        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        List<NavigationItem> items = (List<NavigationItem>) breadcrumbModel.getItems();
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en");
    }

    @Test
    public void testNoGraphqlClient() throws Exception {
        prepareModel("/content/venia/us/en/products/product-page");
        when(breadcrumbResource.adaptTo(ComponentsConfiguration.class)).thenReturn(ComponentsConfiguration.EMPTY);

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        Collection<NavigationItem> items = breadcrumbModel.getItems();

        // If we cannot access Magento data, the breadcrumb should at least display the pages
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en");
    }

    @Test
    public void testProductNotFound() throws Exception {
        graphqlClient = Utils.setupGraphqlClientWithHttpResponseFrom("graphql/magento-graphql-product-not-found-result.json");
        prepareModel("/content/venia/us/en/products/product-page");

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSuffix("/tiberius-gym-tank.html");

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        Collection<NavigationItem> items = breadcrumbModel.getItems();

        // If we cannot access Magento data, the breadcrumb should at least display the pages
        assertThat(items.stream().map(i -> i.getTitle())).containsExactly("en");
    }

    @Test
    public void testCategoryInterfaceComparator() {
        CategoryTree c1 = new CategoryTree();
        c1.setUrlPath("men");
        c1.setUid(new ID("1"));

        CategoryTree c2 = new CategoryTree();
        c2.setUrlPath("men/tops");
        c2.setUid(new ID("2"));

        CategoryTree c3 = new CategoryTree();
        c3.setUrlPath("men/tops/tanks");
        c3.setUid(new ID("3"));

        CategoryTree c4 = new CategoryTree();
        c4.setUrlPath("women/tops");
        c4.setUid(new ID("4"));

        BreadcrumbImpl breadcrumb = new BreadcrumbImpl();
        List<CategoryInterface> categories = Arrays.asList(c4, c3, c2, c1);

        Whitebox.setInternalState(breadcrumb, "structureDepth", 1);
        categories.sort(breadcrumb.getCategoryInterfaceComparator());
        // [men, men/tops/tanks, women/tops, men/tops]
        assertThat(categories).containsExactly(c1, c3, c4, c2);

        Whitebox.setInternalState(breadcrumb, "structureDepth", 2);
        categories.sort(breadcrumb.getCategoryInterfaceComparator());
        // [men/tops, women/tops, men, men/tops/tanks]
        assertThat(categories).containsExactly(c4, c2, c1, c3);

        Whitebox.setInternalState(breadcrumb, "structureDepth", 3);
        categories.sort(breadcrumb.getCategoryInterfaceComparator());
        // [men/tops/tanks, women/tops, men/tops, men]
        assertThat(categories).containsExactly(c3, c4, c2, c1);
    }

    @Test
    public void testJsonRender() throws Exception {
        Utils.setupHttpResponse("graphql/magento-graphql-product-result.json", httpClient, HttpStatus.SC_OK, "{products(filter:{url_key");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient, HttpStatus.SC_OK,
            "{products(filter:{sku");
        Utils.setupHttpResponse("graphql/magento-graphql-product-breadcrumb-result.json", httpClient, HttpStatus.SC_OK,
            "breadcrumbs{category_uid,category_url_path,category_name}");

        prepareModel("/content/venia/us/en/products/product-page");

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSuffix("/tiberius-gym-tank.html");

        breadcrumbModel = context.request().adaptTo(BreadcrumbImpl.class);
        ObjectMapper mapper = new ObjectMapper();
        String expected = Utils.getResource("results/result-datalayer-breadcrumb-component.json");
        String jsonResult = breadcrumbModel.getData().getJson();
        assertEquals(mapper.readTree(expected), mapper.readTree(jsonResult));

        String itemsJsonExpected = Utils.getResource("results/result-datalayer-breadcrumb-items.json");
        Collection<NavigationItem> breadcrumbModelItems = breadcrumbModel.getItems();
        String itemsJsonResult = breadcrumbModelItems
            .stream()
            .map(i -> i.getData().getJson())
            .collect(Collectors.joining(",", "[", "]"));
        assertEquals(mapper.readTree(itemsJsonExpected), mapper.readTree(itemsJsonResult));
    }
}
