package com.artezio.bpm.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class ResourceLoaderTest {

    @Mock
    private AppResourceLoader appResourceloader;
    @Mock
    private DeploymentResourceLoader deploymentResourceLoader;
    @InjectMocks
    private ResourceLoader resourceLoader = new ResourceLoader();

    @Test
    public void testGetProtocol_NoProtocolDefined() {
        String actual = resourceLoader.getProtocol("noProtocolResource");

        assertEquals(ResourceLoader.APP_PROTOCOL, actual);
    }

    @Test
    public void testGetProtocol_AppProtocol() {
        String actual = resourceLoader.getProtocol("embedded:app:aResource");

        assertEquals(ResourceLoader.APP_PROTOCOL, actual);
    }

    @Test
    public void testGetProtocol_DeploymentProtocol() {
        String actual = resourceLoader.getProtocol("embedded:deployment:aResource");

        assertEquals(ResourceLoader.DEPLOYMENT_PROTOCOL, actual);
    }

    @Test
    public void getFormPath_WithProtocolInKey() {
        String actual = resourceLoader.getFormPath("embedded:deployment:aResource");

        assertEquals("aResource", actual);
    }

    @Test
    public void getFormPath_WithoutProtocolInKey() {
        String actual = resourceLoader.getFormPath("aResource");

        assertEquals("aResource", actual);
    }

    @Test
    public void testListResources_App() throws MalformedURLException {
        String resourcesPath = "forms";
        List<String> mackResources = Arrays.asList("a", "b");
        List<String> expecteds = Arrays.asList("embedded:app:a", "embedded:app:b");
        when(appResourceloader.listResources(any(), any())).thenReturn(mackResources);

        List<String> actuals = resourceLoader.listResources(null, "embedded:app:", resourcesPath);

        verify(appResourceloader).listResources(any(), any());
        assertEquals(expecteds, actuals);
    }

    @Test
    public void testListResources_Deployment() {
        String resourcesPath = "forms";
        List<String> mockResources = Arrays.asList("a", "b");
        List<String> expecteds = Arrays.asList("embedded:deployment:a", "embedded:deployment:b");
        when(deploymentResourceLoader.listResources(any(), any())).thenReturn(mockResources);

        List<String> actuals = resourceLoader.listResources(null, "embedded:deployment:", resourcesPath);

        verify(deploymentResourceLoader).listResources(any(), any());
        assertEquals(expecteds, actuals);
    }

    @Test
    public void testGetResource_App() {
        InputStream expected = new ByteArrayInputStream("a".getBytes());
        when(appResourceloader.getResource(any(), any())).thenReturn(expected);

        InputStream actual = resourceLoader.getResource(null, "embedded:app:test.json");

        verify(appResourceloader).getResource(any(), any());
        assertSame(expected, actual);

    }

    @Test
    public void testGetResource_Deployment() {
        InputStream expected = new ByteArrayInputStream("a".getBytes());
        when(deploymentResourceLoader.getResource(any(), any())).thenReturn(expected);

        InputStream actual = resourceLoader.getResource(null, "embedded:deployment:test.json");

        verify(deploymentResourceLoader).getResource(any(), any());
        assertSame(expected, actual);

    }

    @Test
    public void testGetResource_WithoutProtocol() {
        InputStream expected = new ByteArrayInputStream("a".getBytes());
        when(appResourceloader.getResource(any(), any())).thenReturn(expected);

        InputStream actual = resourceLoader.getResource(null, "test.json");

        verify(appResourceloader).getResource(any(), any());
        assertSame(expected, actual);

    }

    @Test
    public void testGetResource_WithProtocolOverrided() {
        InputStream expected = new ByteArrayInputStream("a".getBytes());
        when(appResourceloader.getResource(any(), any())).thenReturn(expected);

        InputStream actual = resourceLoader.getResource(null, "embedded:app:", "embedded:deployment:test.json");

        verify(appResourceloader).getResource(any(), any());
        assertSame(expected, actual);

    }

    @Test
    public void testGetResource_WithProtocolOverridedForNonProtocolResource() {
        InputStream expected = new ByteArrayInputStream("a".getBytes());
        when(deploymentResourceLoader.getResource(any(), any())).thenReturn(expected);

        InputStream actual = resourceLoader.getResource(null, "embedded:deployment:", "test.json");

        verify(deploymentResourceLoader).getResource(any(), any());
        assertSame(expected, actual);

    }

}
