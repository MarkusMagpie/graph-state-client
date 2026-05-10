package com.graphstate.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GraphStateClient {
    private static final String BASE_URL = "http://localhost:5000";
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> buildState(String vertices, String edges) throws IOException {
        String url = BASE_URL + "/build_state";
        Map<String, Object> request = Map.of("vertices", parseVertices(vertices),
                "edges", parseEdges(edges));

        return post(url, request);
    }

    // "1,2,3" -> List<Integer>
    public Object parseVertices(String verticesStr) {
        List<Integer> vertices = Arrays.stream(verticesStr.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        return vertices;
    }

    // "1-2 2-3" -> List<List<Integer>>
    public Object parseEdges(String edgesStr) {
        List<List<Integer>> edges = Arrays.stream(edgesStr.split(" "))
                .map(pair -> pair.split("-"))
                .map(parts -> List.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
                .collect(Collectors.toList());

        return edges;
    }

    public Map<String, Object> checkGraphState(int n, List<String> signs) throws IOException {
        String url = BASE_URL + "/check_graph_submit";
        Map<String, Object> request = Map.of("n", n, "signs", signs);

        return post(url, request);
    }



    // метод для отправки сериализованного http запроса, получения http ответа и его десериализации
    // https://habr.com/ru/companies/otus/articles/687004/
    public Map<String, Object> post(String url, Map<String, Object> data) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        String json = mapper.writeValueAsString(data); // POJO -> JSON сериализация
        // пример: {"vertices": [1,2,3,4], "edges": [[1,2],[2,3],[3,4]]}
        HttpPost post_request = new HttpPost(url);
        post_request.setEntity(new StringEntity(json));
        post_request.setHeader("Content-Type", "application/json");

        var http_response = client.execute(post_request);
        String jsonResponse = EntityUtils.toString(http_response.getEntity());

        return mapper.readValue(jsonResponse, Map.class); // JSON -> POJO десериализация ответа

    }
}