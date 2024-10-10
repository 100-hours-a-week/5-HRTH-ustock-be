package com.hrth.ustock.service.auth;

import com.hrth.ustock.dto.oauth2.CustomOAuth2User;
import com.hrth.ustock.dto.oauth2.GoogleResponse;
import com.hrth.ustock.dto.oauth2.OAuth2Response;
import com.hrth.ustock.dto.oauth2.UserOauthDto;
import com.hrth.ustock.entity.main.User;
import com.hrth.ustock.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String ROLE_USER = "ROLE_USER";
    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest oAuth2UserRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(oAuth2UserRequest);
        OAuth2Response oAuth2Response = getOAuth2Response(oAuth2UserRequest, oAuth2User);

        User existData = userRepository.findByProviderAndProviderId(
                oAuth2Response.getProvider(), oAuth2Response.getProviderId()
        );

        if (existData == null) {
            User newUser = User.builder()
                    .providerName(oAuth2Response.getName())
                    .provider(oAuth2Response.getProvider())
                    .providerId(oAuth2Response.getProviderId())
                    .providerImage(oAuth2Response.getPicture())
                    .role(ROLE_USER)
                    .build();
            User saved = userRepository.save(newUser);
            return new CustomOAuth2User(saved.toOAuthDto());
        } else {
            UserOauthDto userOauthDTO = UserOauthDto.builder()
                    .providerName(oAuth2User.getAttribute("name"))
                    .profile(oAuth2User.getAttribute("picture"))
                    .build();
            existData.updateUserOAuth(userOauthDTO);
            return new CustomOAuth2User(existData.toOAuthDto());
        }
    }

    private static OAuth2Response getOAuth2Response(
            OAuth2UserRequest oAuth2UserRequest, OAuth2User oAuth2User) {

        String registrationId = oAuth2UserRequest
                .getClientRegistration()
                .getRegistrationId();

        OAuth2Response oAuth2Response;
        if (registrationId.equals("google")) {
            oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());
        } else {
            OAuth2Error error = new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_CLIENT, "Unsupported provider", null
            );
            throw new OAuth2AuthenticationException(error);
        }
        return oAuth2Response;
    }
}
