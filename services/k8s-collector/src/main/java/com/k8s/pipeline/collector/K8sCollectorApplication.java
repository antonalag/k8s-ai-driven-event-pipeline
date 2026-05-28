package com.k8s.pipeline.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class K8sCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(K8sCollectorApplication.class, args);
    }
}
