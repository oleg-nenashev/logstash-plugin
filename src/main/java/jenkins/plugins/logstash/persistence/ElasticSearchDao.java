/*
 * The MIT License
 *
 * Copyright 2014 Barnes and Noble College
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.logstash.persistence;

import hudson.model.Run;
import static com.google.common.collect.Ranges.closedOpen;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.utils.URIBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import jenkins.plugins.logstash.util.UniqueIdHelper;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.HTTP;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.google.common.collect.Range;

import jenkins.plugins.logstash.configuration.ElasticSearch;

/**
 * Elastic Search Data Access Object.
 *
 * @author Liam Newman
 * @since 1.0.4
 */
public class ElasticSearchDao extends AbstractLogstashIndexerDao {
  private transient HttpClientBuilder clientBuilder;
  private final URI uri;
  private final String auth;
  private final Range<Integer> successCodes = closedOpen(200,300);

  private String username;
  private String password;

  //primary constructor used by indexer factory
  public ElasticSearchDao(URI uri, String username, String password) {
    this(null, uri, username, password);
  }

  // Factored for unit testing
  ElasticSearchDao(HttpClientBuilder factory, URI uri, String username, String password) {

    if (uri == null)
    {
      throw new IllegalArgumentException("uri field must not be empty");
    }

    this.uri = uri;
    this.username = username;
    this.password = password;


    try
    {
      uri.toURL();
    }
    catch (MalformedURLException e)
    {
      throw new IllegalArgumentException(e);
    }

    if (StringUtils.isNotBlank(username)) {
      auth = Base64.encodeBase64String((username + ":" + StringUtils.defaultString(password)).getBytes(StandardCharsets.UTF_8));
    } else {
      auth = null;
    }

    clientBuilder = factory;
  }

  HttpClientBuilder clientBuilder() {
      if (clientBuilder == null) {
          clientBuilder = HttpClientBuilder.create();
      }
      return clientBuilder;
  }

  public URI getUri()
  {
    return uri;
  }

  public String getHost()
  {
    return uri.getHost();
  }

  public String getScheme()
  {
    return uri.getScheme();
  }

  public int getPort()
  {
    return uri.getPort();
  }

  public String getUsername()
  {
    return username;
  }

  public String getPassword()
  {
      return password;
  }

  public String getKey()
  {
    return uri.getPath();
  }

  String getAuth()
  {
    return auth;
  }

  protected HttpPost getHttpPost(String data) {
    HttpPost postRequest;
    postRequest = new HttpPost(uri);
    StringEntity input = new StringEntity(data, ContentType.APPLICATION_JSON);
    postRequest.setEntity(input);
    if (auth != null) {
      postRequest.addHeader("Authorization", "Basic " + auth);
    }
    return postRequest;
  }

  @Override
  public void push(String data) throws IOException {
    CloseableHttpClient httpClient = null;
    CloseableHttpResponse response = null;
    HttpPost post = getHttpPost(data);

    try {
      httpClient = clientBuilder().build();
      response = httpClient.execute(post);

      if (!successCodes.contains(response.getStatusLine().getStatusCode())) {
        throw new IOException(this.getErrorMessage(response));
      }
    } finally {
      if (response != null) {
        response.close();
      }
      if (httpClient != null) {
        httpClient.close();
      }
    }
  }

    @Override
    public Collection<String> pullLogs(Run run, long sinceMs, long toMs) throws IOException {
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        
      // Determine job id
      String jobId = UniqueIdHelper.getOrCreateId(run);
        
        // Prepare query
        String query = "{\n" +
            "  \"fields\": [\"message\",\"@timestamp\"], \n" +
            "  \"size\": 9999, \n" + // TODO use paging https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html
            "  \"query\": { \n" +
            "    \"bool\": { \n" +
            "      \"must\": [\n" +
            "        { \"match\": { \"data.jobId\":   \"" + jobId + "\"}}, \n" +
            "        { \"match\": { \"data.buildNum\": \"" + run.getNumber() + "\" }}  \n" +
            "      ]\n" +
            "    }\n" +
            "  }\n" +
            "}";
        
        
        // Prepare request   
        final HttpGetWithData getRequest = new HttpGetWithData(uri + "/_search");
        final StringEntity input = new StringEntity(query, ContentType.APPLICATION_JSON);
        getRequest.setEntity(input);
        if (auth != null) {
          getRequest.addHeader("Authorization", "Basic " + auth);
        }
        
        try {
            httpClient = clientBuilder().build();
            response = httpClient.execute(getRequest);

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException(this.getErrorMessage(response));
            }
            
            // TODO: retrieve log entries
            final String content;
            try(InputStream i = response.getEntity().getContent()) {
                content = IOUtils.toString(i);
            }
            
            final JSONObject json = JSONObject.fromObject(content);
            JSONArray jsonArray = json.getJSONObject("hits").getJSONArray("hits");
            ArrayList<String> res = new ArrayList<>(jsonArray.size());
            for (int i=0; i<jsonArray.size(); ++i) {
                JSONObject hit = jsonArray.getJSONObject(i);
                String timestamp = hit.getJSONObject("fields").getJSONArray("@timestamp").getString(0);
                String message = hit.getJSONObject("fields").getJSONArray("message").getString(0);
                res.add(timestamp + " > " +message);
            }
            Collections.sort(res);
            return res;
            
        } finally {
            if (response != null) {
                response.close();
            }
            if (httpClient != null) {
                httpClient.close();
            }
        }
    }
  
  

  private String getErrorMessage(CloseableHttpResponse response) {
    ByteArrayOutputStream byteStream = null;
    PrintStream stream = null;
    try {
      byteStream = new ByteArrayOutputStream();
      stream = new PrintStream(byteStream, true, StandardCharsets.UTF_8.name());

      try {
        stream.print("HTTP error code: ");
        stream.println(response.getStatusLine().getStatusCode());
        stream.print("URI: ");
        stream.println(uri.toString());
        stream.println("RESPONSE: " + response.toString());
        response.getEntity().writeTo(stream);
      } catch (IOException e) {
        stream.println(ExceptionUtils.getStackTrace(e));
      }
      stream.flush();
      return byteStream.toString(StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException e)
    {
      return ExceptionUtils.getStackTrace(e);
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
  }

  private static class HttpGetWithData extends HttpGet implements HttpEntityEnclosingRequest {
    private HttpEntity entity;

    public HttpGetWithData(String uri) {
        super(uri);
    }

    @Override
    public HttpEntity getEntity() {
        return this.entity;
    }

    @Override
    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean expectContinue() {
        final Header expect = getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        return expect != null && HTTP.EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue());
    }
  }

  @Override
  public String getDescription()
  {
    return uri.toString();
  }
}
