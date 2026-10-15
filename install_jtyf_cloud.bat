@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

:: PowerShell 中无需注册表操作，直接启用 ANSI 支持
set "COLOR_GREEN=[32m"
set "COLOR_YELLOW=[33m"
set "COLOR_ORANGE=[38;5;208m"
set "COLOR_RED=[31m"
set "COLOR_RESET=[0m"
set "COLOR_BOLD=[1m"

:: ==========================================
:: 核心配置
:: ==========================================
set "PROJECT_ROOT=%cd%"
set "MODULE_VERSION=2.3.0-SNAPSHOT"

:: 模块配置：格式为"模块名:类型"（类型可为jar或pom）
set "MODULES=jtyf-dependencies:pom,jtyf-framework/jtyf-spring-boot-starter-license:jar,jtyf-framework/jtyf-spring-boot-starter-websocket:jar,jtyf-module-3d:jar"

:: ==========================================
:: 环境检查
:: ==========================================
echo %COLOR_ORANGE%[1/3] 检查构建环境...%COLOR_RESET%

:: 检查Maven
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %COLOR_RED%[ERROR] 未找到Maven命令，请确保Maven已安装并添加到PATH环境变量！%COLOR_RESET%
    goto :error
)

:: 获取Maven版本和路径
for /f "tokens=*" %%a in ('where mvn') do set "MAVEN_PATH=%%a"
for %%a in ("%MAVEN_PATH%\..\..") do set "MAVEN_HOME=%%~fa"
for %%a in ("%MAVEN_HOME%") do set "MAVEN_BASE=%%~nxa"
set "MAVEN_VERSION=!MAVEN_BASE:apache-maven-=!"

:: ==========================================
:: 从 Maven 安装路径下的 conf 获取 settings.xml
:: ==========================================
set "MAVEN_SETTINGS=%MAVEN_HOME%\conf\settings.xml"
set "MAVEN_REPO_BASE="
:: 新增临时变量
set "temp_repo_base="

if exist "%MAVEN_SETTINGS%" (
    echo 正在从 %MAVEN_SETTINGS% 读取本地仓库路径...

    for /f "tokens=*" %%a in ('type "%MAVEN_SETTINGS%" ^| findstr /i /c:"<localRepository>"') do (
        set "line=%%a"

        :: 更健壮的标签内容提取（支持换行和缩进）
        set "line=!line:<localRepository>=!"
        set "line=!line:</localRepository>=!"
        set "line=!line: =!"
        set "line=!line:^<=!"
        set "line=!line:^>=!"

        :: 赋值给临时变量
        set "temp_repo_base=!line!"
    )

    :: 循环结束后，将临时变量的值赋给正式变量
    set "MAVEN_REPO_BASE=!temp_repo_base!"

    if defined MAVEN_REPO_BASE (
        echo %COLOR_GREEN%[SUCCESS]%COLOR_RESET% 已获取基础仓库路径: %COLOR_BOLD%!MAVEN_REPO_BASE!%COLOR_RESET%
    ) else (
        echo %COLOR_YELLOW%[WARNING]%COLOR_RESET% settings.xml 中未定义本地仓库路径，使用默认路径
        set "MAVEN_REPO_BASE=%USERPROFILE%\.m2\repository"
    )
) else (
    echo %COLOR_RED%[ERROR]%COLOR_RESET% 未找到 Maven 配置文件: %MAVEN_SETTINGS%
    goto :error
)

:: ==========================================
:: 从根 pom.xml 获取 groupId
:: ==========================================
set "GROUP_ID=cn.iocoder.cloud"
set "ROOT_POM=%PROJECT_ROOT%\pom.xml"

if exist "%ROOT_POM%" (
    echo 正在从 %ROOT_POM% 读取 groupId...

    set "GROUP_ID_FOUND=0"
    for /f "tokens=*" %%a in ('type "%ROOT_POM%" ^| findstr /i /c:"<groupId>"') do (
        if "!GROUP_ID_FOUND!"=="0" (
            set "line=%%a"
            set "line=!line:<groupId>=!"
            set "line=!line:</groupId>=!"
            set "line=!line: =!"
            set "GROUP_ID=!line!"
            set "GROUP_ID_FOUND=1"
        )
    )

    if "!GROUP_ID_FOUND!"=="1" (
        echo %COLOR_GREEN%[SUCCESS]%COLOR_RESET% 已获取 groupId: %COLOR_BOLD%%GROUP_ID%%COLOR_RESET%
    ) else (
        echo %COLOR_YELLOW%[WARNING]%COLOR_RESET% 在 pom.xml 中未找到项目根节点的 groupId，使用默认值
    )
) else (
    echo %COLOR_YELLOW%[WARNING]%COLOR_RESET% 未找到根 pom.xml，使用默认 groupId: %COLOR_BOLD%%GROUP_ID%%COLOR_RESET%
)

:: 将 groupId 转换为路径格式
set "GROUP_PATH=!GROUP_ID:.=\!"
set "MAVEN_REPO=!MAVEN_REPO_BASE!\!GROUP_PATH!"

:: ==========================================
:: 继续环境检查
:: ==========================================
:: 检查Java
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %COLOR_RED%[ERROR]%COLOR_RESET% 未找到Java命令，请确保JDK已安装并添加到PATH环境变量！
    goto :error
)

:: 获取Java路径
for /f "tokens=*" %%a in ('where java') do set "JAVA_PATH=%%a"

:: 从路径提取JDK版本
for %%a in ("%JAVA_PATH%\..\..") do set "JAVA_HOME=%%~fa"
for %%a in ("%JAVA_HOME%") do set "JAVA_BASE=%%~nxa"
set "JAVA_MAJOR=!JAVA_BASE:jdk-=!"

:: 显示环境信息
echo.
echo ==========================================
echo        环境检查通过！配置信息如下：
echo ------------------------------------------
echo Maven 版本: !MAVEN_BASE!
echo Maven 路径: !MAVEN_PATH!
echo Maven 配置文件: %MAVEN_SETTINGS%
echo.
echo JDK 版本: Java !JAVA_MAJOR!
echo JDK 路径: !JAVA_PATH!
echo.
echo GroupId: %COLOR_BOLD%%GROUP_ID%%COLOR_RESET%
echo Group 路径: !GROUP_PATH!
echo 基础仓库路径: %COLOR_BOLD%%MAVEN_REPO_BASE%%COLOR_RESET%
echo 完整仓库路径: %COLOR_BOLD%%MAVEN_REPO%%COLOR_RESET%
echo ==========================================
echo.

:: ==========================================
:: 执行多模块构建
:: ==========================================
echo %COLOR_ORANGE%[2/3] 执行Maven构建...%COLOR_RESET%
echo %COLOR_YELLOW%【提醒】构建可能需要几分钟，请耐心等待...%COLOR_RESET%

:: 动态生成 -pl 参数
set "MVN_MODULES="
for %%m in (%MODULES%) do (
    for /f "tokens=1,2 delims=:" %%a in ("%%m") do (
        if defined MVN_MODULES (
            set "MVN_MODULES=!MVN_MODULES!,%%a"
        ) else (
            set "MVN_MODULES=%%a"
        )
    )
)

call mvn clean install -pl !MVN_MODULES! -am -DskipTests

if %ERRORLEVEL% neq 0 (
    echo %COLOR_RED%[ERROR]%COLOR_RESET% 构建失败！错误代码: %ERRORLEVEL%
    goto :error
)

:: ==========================================
:: 验证构建产物
:: ==========================================
echo %COLOR_ORANGE%[3/3] 验证构建产物...%COLOR_RESET%
set "verify_result=SUCCESS"

for %%m in (%MODULES%) do (
    for /f "tokens=1,2 delims=:" %%a in ("%%m") do set "module_name=%%a" & set "module_type=%%b"

    :: 提取最后一部分作为目录名
    for %%f in ("!module_name!") do set "dir_name=%%~nxf"

    :: 构建验证路径
    set "artifact_path=!MAVEN_REPO!\!dir_name!\%MODULE_VERSION%"

    if "!module_type!"=="pom" (
        set "file_path=!artifact_path!\!dir_name!-%MODULE_VERSION%.pom"
        if exist "!file_path!" (
            echo %COLOR_GREEN%[SUCCESS] POM模块验证通过:%COLOR_RESET%
            echo   - 模块: %COLOR_BOLD%!module_name!%COLOR_RESET%
            echo   - 路径: %COLOR_YELLOW%!file_path!%COLOR_RESET%
        ) else (
            echo %COLOR_RED%[ERROR] POM文件缺失:%COLOR_RESET%
            echo   - 模块: %COLOR_BOLD%!module_name!%COLOR_RESET%
            echo   - 路径: %COLOR_YELLOW%!file_path!%COLOR_RESET%
            set "verify_result=ERROR"
        )
    ) else (
        set "file_path=!artifact_path!\!dir_name!-%MODULE_VERSION%.jar"
        if exist "!file_path!" (
            echo %COLOR_GREEN%[SUCCESS] JAR文件验证通过:%COLOR_RESET%
            echo   - 模块: %COLOR_BOLD%!module_name!%COLOR_RESET%
            echo   - 路径: %COLOR_YELLOW%!file_path!%COLOR_RESET%
        ) else (
            echo %COLOR_RED%[ERROR] JAR文件缺失:%COLOR_RESET%
            echo   - 模块: %COLOR_BOLD%!module_name!%COLOR_RESET%
            echo   - 路径: %COLOR_YELLOW%!file_path!%COLOR_RESET%
            set "verify_result=ERROR"
        )
    )
    :: 添加空行分隔每个模块
    echo.
)

:: 输出最终结果
if "%verify_result%"=="SUCCESS" (
    echo.
    echo ==================================================
    echo        %COLOR_GREEN%%COLOR_BOLD%所有模块构建并验证成功！%COLOR_RESET%
    echo ==================================================
    echo.
    echo 基础仓库路径：%MAVEN_REPO_BASE%
    echo 完整仓库路径：%MAVEN_REPO%
    echo.
    pause
    exit /b 0
) else (
    echo.
    echo ==================================================
    echo        %COLOR_RED%%COLOR_BOLD%构建产物验证失败，请检查上述错误信息！%COLOR_RESET%
    echo ==================================================
    echo.
    pause
    exit /b 1
)

:error
echo.
echo ==================================================
echo        %COLOR_RED%%COLOR_BOLD%脚本执行过程中发生错误！%COLOR_RESET%
echo ==================================================
echo.
pause
exit /b 1