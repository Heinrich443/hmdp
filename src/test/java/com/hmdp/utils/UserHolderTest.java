package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class UserHolderTest {

    @Test
    public void testUserHolder() {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(1L);
        userDTO.setNickName("aaaa");
        userDTO.setIcon("aaaa");
        UserHolder.saveUser(userDTO);

        UserDTO user = UserHolder.getUser();
        System.out.println(user);

        UserHolder.removeUser();

        UserDTO user1 = UserHolder.getUser();
        System.out.println(user1);
    }
}
