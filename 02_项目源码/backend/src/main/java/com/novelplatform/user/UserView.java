package com.novelplatform.user;

public record UserView(long id, String username, String nickname, String bio, String role) {
    public static UserView from(AppUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getBio(), user.getRole());
    }
}
