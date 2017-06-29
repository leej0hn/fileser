package io.communet.fileser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.CountDownLatch;

/**
 * <p>function:
 * <p>User: leejohn
 * <p>Date: 16/7/8
 * <p>Version: 1.0
 */
@SpringBootApplication
@Slf4j
public class WebApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(WebApplication.class);
        application.run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        log.info("started successfully");
        countDownLatch.await();
    }
}