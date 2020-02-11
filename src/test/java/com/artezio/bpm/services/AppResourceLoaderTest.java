package com.artezio.bpm.services;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.List;

import javax.servlet.ServletContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class AppResourceLoaderTest {

    @InjectMocks
    private AppResourceLoader loader = new AppResourceLoader();

    @Mock
    private ServletContext servletContext;

    @Test
    public void testListClassloaderResources() throws MalformedURLException {
	String resourcesPath = "forms";
	when(servletContext.getResource(resourcesPath))
		.thenReturn(Thread.currentThread().getContextClassLoader().getResource(resourcesPath));

	List<String> actuals = loader.listResources(null, resourcesPath);

	assertTrue(actuals.contains("forms/test.json"));
    }

    @Test
    public void testGetResource() throws IOException {
	String resourcesKey = "forms/formWithState.json";
	when(servletContext.getResourceAsStream(resourcesKey))
		.thenReturn(Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcesKey));
	
	InputStream actual = loader.getResource(null, resourcesKey);
	
	assertNotNull(actual);
	assertTrue(actual.available() > 0);
    }

}
