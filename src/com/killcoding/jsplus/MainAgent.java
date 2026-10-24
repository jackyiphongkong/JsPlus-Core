// package com.killcoding.jsplus;

// import java.io.File;
// import com.killcoding.tool.CommonTools;
// import com.killcoding.datasource.DriverDataSource;
// import java.util.Map;
// import java.util.HashMap;
// import com.killcoding.tool.FileTools;
// import java.io.StringReader;
// import java.nio.file.Path;
// import java.nio.file.Files;
// import com.killcoding.cache.CacheArray;
// import java.util.Scanner;
// import java.nio.file.Paths;
// import com.killcoding.tool.ConfigProperties;
// import com.killcoding.jsplus.GhostConsole;
// import net.bytebuddy.agent.ByteBuddyAgent;
// import java.lang.instrument.ClassFileTransformer;
// import java.security.ProtectionDomain;
// import java.net.URLEncoder;
// import java.nio.charset.StandardCharsets;

// public final class MainAgent { 
    
//     public MainAgent(){
//         super();
//     }
    
//     protected static long mainAgentClassCurrentIndex = 0;
//     protected static String mainAgentClassMatches = null;
//     protected static String mainAgentClassExportIndex = null;
//     protected static String mainAgentClassExportPath = null;
        
//     public static void main(String[] args){
//         ByteBuddyAgent.install();
//         mainAgentClassMatches = System.getenv("MainAgentClassMatches") + "";
//         mainAgentClassExportIndex = System.getenv("MainAgentClassExportIndex") + "";
//         mainAgentClassExportPath = System.getenv("MainAgentClassExportPath");
        
//         System.out.println("MainAgentClassMatches: " + mainAgentClassMatches);
//         System.out.println("MainAgentClassExportIndex: " + mainAgentClassExportIndex);
//         System.out.println("MainAgentClassExportPath: " + mainAgentClassExportPath);
//         System.out.println("MainAgentClassExportPath using default (e.g. CurrentFolder/javaClasses)");
        
//         ByteBuddyAgent.getInstrumentation().addTransformer(new ClassFileTransformer() {
//             @Override
//             public byte[] transform(ClassLoader loader, String className,
//                                   Class<?> classBeingRedefined,
//                                   ProtectionDomain protectionDomain,
//                                   byte[] classfileBuffer) {
                
//                 if (className != null && className.matches(mainAgentClassMatches)) {
//                     saveBytecode(className, classfileBuffer);
//                 }
//                 return classfileBuffer;
//             }
            
//             private void saveBytecode(String className, byte[] bytes) {
//                 if(mainAgentClassExportPath == null){
//                     mainAgentClassExportPath = ScriptConsole.getJsPlusPath() + "/javaClasses/";
//                 }
//                 try {
//                     String classFileName = URLEncoder.encode(className.replace('/', '.'), StandardCharsets.UTF_8.toString());
//                     if(mainAgentClassExportIndex.equals("true")){
//                         classFileName = mainAgentClassCurrentIndex + "_" + classFileName;
//                     }
//                     Path path = Paths.get(mainAgentClassExportPath,classFileName + ".class");
//                     Files.createDirectories(path.getParent());
//                     Files.write(path, bytes);
//                 } catch (Exception e) {
//                     e.printStackTrace();
//                 }finally{
//                     mainAgentClassCurrentIndex++;
//                 }
//             }
//         });   
        
//         Main.appMain(args);
//     }
    
// }