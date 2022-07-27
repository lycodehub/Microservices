package cn.ly00.feign.clients;

import cn.ly00.feign.config.DefaultFeignConfiguration;
import cn.ly00.feign.pojo.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "userservice", configuration = DefaultFeignConfiguration.class)
public interface UserClient {
    @GetMapping("/user/{id}")
    User findById(@PathVariable Long id);
}
