package kr.or.kosa.backend.config;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlgorithmVectorStoreConfig {

    @Value("${QDRANT_COLLECTION_ALGORITHM:coai_documents}")
    private String algorithmCollection;

    @Bean("algorithmVectorStore")
    public VectorStore algorithmVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        // Spring AI가 이미 만든 QdrantClient 재사용!
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(algorithmCollection)
                .initializeSchema(true)
                .build();
    }
}