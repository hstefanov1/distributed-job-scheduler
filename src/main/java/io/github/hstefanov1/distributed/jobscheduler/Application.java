package io.github.hstefanov1.distributed.jobscheduler;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Application {

    public static void main(String[] args) {
        Container.run();
        Quarkus.run(args);
    }
}
