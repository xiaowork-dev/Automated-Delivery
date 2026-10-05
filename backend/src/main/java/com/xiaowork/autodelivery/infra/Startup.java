package com.xiaowork.autodelivery.infra;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class Startup implements ApplicationRunner {
    private final BootstrapService bootstrap;
    @Override public void run(ApplicationArguments args) {bootstrap.initialize();}
}
