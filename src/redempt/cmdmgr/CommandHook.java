package redempt.cmdmgr;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandHook {
	
	String value();
	
}
