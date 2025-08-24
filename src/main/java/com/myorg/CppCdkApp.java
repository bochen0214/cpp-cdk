package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;

public class CppCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new CppCdkStack(app, "CppCdkStack");
        new CppPipelineStack(app, "CppPipelineStack", StackProps.builder().build());

        app.synth();
    }
}

