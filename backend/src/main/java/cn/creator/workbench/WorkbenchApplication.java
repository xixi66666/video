package cn.creator.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@MapperScan("cn.creator.workbench.persistence")
public class WorkbenchApplication {
    public static void main(String[] args) { SpringApplication.run(WorkbenchApplication.class, args); }
}
