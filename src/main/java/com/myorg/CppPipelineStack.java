package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.constructs.Construct;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;
import software.amazon.awscdk.services.codebuild.ComputeType;


import java.util.List;
import software.amazon.awscdk.SecretValue;

public class CppPipelineStack extends Stack {
    public CppPipelineStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CppPipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // 1. 定义 CodeBuild 项目
        PipelineProject buildProject = PipelineProject.Builder.create(this, "CppBuilderProject")
                .buildSpec(BuildSpec.fromObject(new java.util.HashMap<String, Object>() {{
                    put("version", "0.2");
                    put("phases", new java.util.HashMap<String, Object>() {{
                        put("install", new java.util.HashMap<String, Object>() {{
                            put("commands", List.of(
                                    "echo Skipping Maven install because image already has it"
                            ));
                        }});
                        put("build", new java.util.HashMap<String, Object>() {{
                            put("commands", List.of(
                                    "echo Building project...",
                                    "mvn clean package -DskipTests",
                                    "npx aws-cdk deploy CppCdkStack --require-approval never"
                            ));
                        }});
                    }});
                    put("artifacts", new java.util.HashMap<String, Object>() {{
                        put("files", List.of("**/*"));
                    }});
                }}))
                .buildSpec(BuildSpec.fromSourceFilename("buildspec.yml"))
                .environment(BuildEnvironment.builder()
                        .buildImage(LinuxBuildImage.STANDARD_5_0)
                        .computeType(ComputeType.SMALL)
                        .build())
                .timeout(Duration.minutes(30))
                .build();

        // 2. CodeBuild 权限
        buildProject.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("cloudformation:*", "s3:*", "lambda:*", "dynamodb:*", "apigateway:*", "iam:*"))
                .resources(List.of("*"))
                .build());

        // 3. 创建 Pipeline
        Artifact sourceOutput = new Artifact();
        Artifact buildOutput = new Artifact();

        // 4. 使用 GitHub 作为 Source
        CodeStarConnectionsSourceAction githubSource = CodeStarConnectionsSourceAction.Builder.create()
                .actionName("GitHub_Source")
                .owner("bochen0214")  // GitHub 用户名
                .repo("cpp-cdk")      // 仓库名
                .branch("main") // 分支
                .connectionArn("arn:aws:codeconnections:us-east-2:235494800347:connection/d7bbb5ef-bfbd-4054-8c5f-2ca41a148d67")
                .output(sourceOutput)
                .build();

        // 5. 构建 Pipeline
        Pipeline pipeline = Pipeline.Builder.create(this, "CppPipeline")
                .pipelineName("CppPipeline")
                .stages(List.of(
                        StageProps.builder()
                                .stageName("Source")
                                .actions(List.of(githubSource))
                                .build(),
                        StageProps.builder()
                                .stageName("BuildAndDeploy")
                                .actions(List.of(CodeBuildAction.Builder.create()
                                        .actionName("Build")
                                        .project(buildProject)
                                        .input(sourceOutput)
                                        .outputs(List.of(buildOutput))
                                        .build()))
                                .build()
                ))
                .build();
    }

}
