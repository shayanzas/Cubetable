package dev.tacticalcombat.uiapi;

/** No throwable messages, causes or payloads: those may contain private check/sheet data. */
public final class UiDiagnostics {
    private UiDiagnostics() {}
    public static boolean recordValidation(RuntimeException failure){
        // Gson wraps record-constructor validation in RuntimeException -> InvocationTargetException.
        return failure.getClass()==RuntimeException.class && failure.getCause() instanceof java.lang.reflect.InvocationTargetException invocation && invocation.getCause() instanceof IllegalArgumentException;
    }
    public static String context(String operation,Throwable failure){
        String op;try{op=UiApiService.Operation.valueOf(operation).name();}catch(RuntimeException invalid){op="UNKNOWN";}
        var out=new StringBuilder("UI fault operation=").append(op).append(" category=").append(failure.getClass().getSimpleName());
        var stack=failure.getStackTrace();for(int i=0;i<Math.min(8,stack.length);i++)out.append("\n at ").append(stack[i]);
        return out.toString();
    }
    public static UiApiService.Reply fault(String operation,RuntimeException failure){
        String reference=java.util.UUID.randomUUID().toString().substring(0,8);
        org.slf4j.LoggerFactory.getLogger(UiDiagnostics.class).error("{} reference={}",context(operation,failure),reference);
        return new UiApiService.Reply("SERVER_ERROR","Server UI fault (reference "+reference+"). Refresh; report this reference if it persists.",new com.google.gson.JsonObject());
    }
}
