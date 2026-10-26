var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var Runnable = Java.type("java.lang.Runnable");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var Str = Java.type("java.lang.String");
var RemoteFile = Java.type("com.killcoding.file.RemoteFile");
var CacheArray = Java.type("com.killcoding.cache.CacheArray");
var DiskFile = Java.type("com.killcoding.file.DiskFile");
var BaseFile = Java.type("com.killcoding.file.BaseFile");
var ConfigProperties = Java.type("com.killcoding.tool.ConfigProperties");
var DriverExecutor = Java.type("com.killcoding.datasource.DriverExecutor");
var CacheDriverExecutor = Java.type("com.killcoding.datasource.CacheDriverExecutor");
var CommonTools = Java.type("com.killcoding.tool.CommonTools");

var RF_GCA = Console.getGlobalCacheArray(0,1);
RF_GCA.setNulIsCompleted(false);
Console.getContext().put("RF_GCA",RF_GCA);

var CB6 = Java.extend(Runnable, {
    run: function () {
        var o = RF_GCA.getObject();
        if(o !== null && o !== undefined) {
            try {
                var rf = new RemoteFile(o.file_parent_path + '/' + o.file_name);
                var content = rf.readAllString();
                rf.forceDelete();
                print(RF_GCA.getProcessedTotal() + " ------------------------------- " + content);
            } catch (e) {
                // RF_errors.add('' + e);
                print(e);
            }
        }   
    }
});
Console.callback(new CB6());
