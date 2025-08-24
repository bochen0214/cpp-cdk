package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.apigateway.StepFunctionsRestApi;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.stepfunctions.*;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvokeProps;
import software.constructs.Construct;
import software.amazon.awscdk.services.logs.LogGroup;


import java.util.Map;

public class CppCdkStack extends Stack {
    public CppCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CppCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // create DynamoDb table
        Table priceTable = Table.Builder.create(this, "PriceTable")
                .partitionKey(Attribute.builder()
                        .name("timestamp")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // create lambda function
        // 1. 创建 Lambda
        Function formatDataLambda = Function.Builder.create(this, "FormatDataLambda")
                .runtime(Runtime.JAVA_17)
                .handler("com.bochen.cpp.FormatDataHandler::handleRequest")
                .code(Code.fromAsset("lambda/target/lambda-1.0-SNAPSHOT.jar"))
                .memorySize(512)
                .timeout(Duration.seconds(20))
                .build();

        Function riskCheckLambda = Function.Builder.create(this, "RiskCheckLambda")
                .runtime(Runtime.JAVA_17)
                .handler("com.bochen.cpp.RiskCheckHandler::handleRequest")
                .code(Code.fromAsset("lambda/target/lambda-1.0-SNAPSHOT.jar"))
                .memorySize(512)
                .timeout(Duration.seconds(20))
                .build();

        Function writeDynamoLambda = Function.Builder.create(this, "WriteDynamoLambda")
                .runtime(Runtime.JAVA_17)
                .handler("com.bochen.cpp.WriteDynamoHandler::handleRequest")
                .code(Code.fromAsset("lambda/target/lambda-1.0-SNAPSHOT.jar"))
                .memorySize(512)
                .timeout(Duration.seconds(20))
                .environment(Map.of("PRICE_TABLE", priceTable.getTableName()))
                .build();

        // lambda 写入 DynamoDB
        priceTable.grantWriteData(writeDynamoLambda);

        // 2. 定义 Step Function 任务
        TaskStateBase formatDataTask = new LambdaInvoke(this, "Format Price Data",
                LambdaInvokeProps.builder()
                        .lambdaFunction(formatDataLambda)
                        .outputPath("$.Payload")
                        .build());

        TaskStateBase riskCheckTask = new LambdaInvoke(this, "Risk Check",
                LambdaInvokeProps.builder()
                        .lambdaFunction(riskCheckLambda)
                        .outputPath("$.Payload")
                        .build());

        TaskStateBase writeDynamoTask = new LambdaInvoke(this, "Write DynamoDB",
                LambdaInvokeProps.builder()
                        .lambdaFunction(writeDynamoLambda)
                        .outputPath("$.Payload")
                        .build());

        // 3. 串联步骤
        Chain chain = formatDataTask.next(riskCheckTask).next(writeDynamoTask);

        LogGroup logGroup = LogGroup.Builder.create(this, "CppStepFnLogGroup")
                .retention(RetentionDays.ONE_WEEK)  // 日志保留时间
                .removalPolicy(RemovalPolicy.DESTROY) // 删除 stack 时删除日志
                .build();

        // 4. 创建状态机
        StateMachine stateMachine = StateMachine.Builder.create(this, "CppStateMachine")
                .definition(chain)
                .timeout(Duration.minutes(5))
                .logs(
                        software.amazon.awscdk.services.stepfunctions.LogOptions.builder()
                                .destination(logGroup)
                                .level(LogLevel.ALL) // 记录所有日志：input、output、error
                                .includeExecutionData(true)
                                .build()
                )
                .stateMachineType(StateMachineType.EXPRESS)
                .build();

        // 创建API gateway
        StepFunctionsRestApi api = StepFunctionsRestApi.Builder.create(this, "CppApi")
                .stateMachine(stateMachine)
                .build();

        // 输出endpoint
        CfnOutput.Builder.create(this, "CppEndPoint")
                .description("CompetitivePricingPublisher API Endpoint")
                .value(api.getUrl())
                .build();

    }

}
