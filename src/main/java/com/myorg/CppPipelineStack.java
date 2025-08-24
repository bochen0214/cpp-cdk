package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codecommit.Repository;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.constructs.Construct;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;


import java.util.List;

public class CppPipelineStack extends Stack {
    public CppPipelineStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CppPipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // 1 创建 codeCommit 仓库
        Repository repo = Repository.Builder.create(this, "CppRepo")
                .repositoryName("cpp-repo")
                .build();

        // 2 定义 codeBuild 项目
        PipelineProject buildProject = PipelineProject.Builder.create(this, "CppBuilderProject")
                .buildSpec(BuildSpec.fromObject(new java.util.HashMap<String, Object>() {{
                    put("version", "0.2");
                    put("phases", new java.util.HashMap<String, Object>() {{
                        put("install", new java.util.HashMap<String, Object>() {{
                            put("commands", List.of(
                                    "echo Installing Maven...",
                                    "yum install -y maven"
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
                .environment(BuildEnvironment.builder()
                        .buildImage(LinuxBuildImage.STANDARD_6_0)
                        .computeType(ComputeType.SMALL)
                        .build())
                .timeout(Duration.minutes(30))
                .build();

        // 3. CodeBuild 权限
        buildProject.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("cloudformation:*", "s3:*", "lambda:*", "dynamodb:*", "apigateway:*", "iam:*"))
                .resources(List.of("*"))
                .build());

        // 4. 创建 Pipeline
        Artifact sourceOutput = new Artifact();
        Artifact buildOutput = new Artifact();

        Pipeline pipeline = Pipeline.Builder.create(this, "CppPipeline")
                .pipelineName("CppPipeline")
                .stages(List.of(
                        StageProps.builder()
                                .stageName("Source")
                                .actions(List.of(CodeCommitSourceAction.Builder.create()
                                        .actionName("CodeCommit")
                                        .repository(repo)
                                        .output(sourceOutput)
                                        .branch("main")
                                        .build()))
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
