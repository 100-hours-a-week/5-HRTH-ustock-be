package com.hrth.ustock.controller.api;

import com.hrth.ustock.dto.game.hint.GameHintResponseDto;
import com.hrth.ustock.dto.game.interim.GameInterimResponseDto;
import com.hrth.ustock.dto.game.result.GameRankingDto;
import com.hrth.ustock.dto.game.result.GameResultResponseDto;
import com.hrth.ustock.dto.game.result.GameResultStockDto;
import com.hrth.ustock.dto.game.stock.GameStockInfoResponseDto;
import com.hrth.ustock.dto.game.stock.GameTradeRequestDto;
import com.hrth.ustock.dto.game.user.GameUserResponseDto;
import com.hrth.ustock.entity.game.HintLevel;
import com.hrth.ustock.exception.common.ExceptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Skrrr Game", description = "Skrrr 게임 관련 API")
public interface GameApi {
    @Operation(
            summary = "게임 정보 초기화",
            description = "게임을 시작할 때 반드시 호출, 진행 중인 게임이 있다면 덮어쓰고 새로 시작합니다."
    )
    @Parameter(
            name = "nickname",
            description = "게임에서 사용할 임시 닉네임, 중복 체크는 하지 않습니다.",
            required = true
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 후 이용 가능",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 사용자를 찾을 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "종목 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    ResponseEntity<List<GameStockInfoResponseDto>> startGame(@RequestParam String nickname);

    @Operation(
            summary = "유저 정보 조회 요청",
            description = "현재 연도의 유저 정보를 가져옵니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 후 이용 가능",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "게임 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "JSON->Object 역직렬화에 실패했습니다",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    ResponseEntity<GameUserResponseDto> showUser();

    @Operation(
            summary = "종목 거래 요청",
            description = "종목 아이디, 수량, 매도/매수 여부를 바탕으로 거래를 진행 (매도: SELL, 매수: BUY)"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잔액이 부족합니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "종목을 보유하고 있지 않습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "수량이 부족합니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 후 이용 가능",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "게임 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "종목 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Object->JSON 직렬화에 실패했습니다",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "JSON->Object 역직렬화에 실패했습니다",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    ResponseEntity<Void> tradeStock(@RequestBody GameTradeRequestDto requestDto);

    @Operation(
            summary = "다음 연도로 진행",
            description = "다음 연도를 기준으로 정보 갱신 후 유저 정보 + 종목 리스트 받아옴"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 후 이용 가능",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "게임 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "종목 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Object->JSON 직렬화에 실패했습니다",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "JSON->Object 역직렬화에 실패했습니다",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    ResponseEntity<GameInterimResponseDto> showInterimResult();

    @Operation(
            summary = "정보 거래소 힌트 조회",
            description = "힌트는 ONE, TWO, THREE 한번씩 구매 가능합니다."
    )
    @Parameters({
            @Parameter(
                    name = "stockId",
                    description = "종목 아이디"),
            @Parameter(
                    name = "hintLevel",
                    description = "힌트 단계 ( ONE, TWO, THREE )")
    })
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잔액이 부족합니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "해당 힌트를 이미 구매하셨습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 후 이용 가능",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "게임 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "종목의 연도 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "종목 힌트를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    ResponseEntity<GameHintResponseDto> showHint(@RequestParam long stockId, @RequestParam HintLevel hintLevel);

    @Operation(
            summary = "최종 결과 조회",
            description = "최종 결과를 순위대로 반환 (사용자: USER, AI: COM)"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 후 이용 가능",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "게임 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "JSON->Object 역직렬화에 실패했습니다",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    ResponseEntity<List<GameResultResponseDto>> showResult();

    @Operation(
            summary = "게임 내 종목 리스트 조회",
            description = "게임에 사용된 종목의 리스트를 반환, 차트는 /v1/stocks/{code}/chart를 이용해주세요. " +
                    "stockName은 원래의 종목명을 의미하며, inGameName은 게임에서 사용된 종목명을 의미합니다. " +
                    "종목 리스트와 각 종목별 10년치 가격, 힌트의 모티브가 된 뉴스를 리스트로 보내드립니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "[\n" +
                                            "  {\n" +
                                            "    \"stockId\": 1,\n" +
                                            "    \"fakeName\": \"A 게임\",\n" +
                                            "    \"realName\": \"넥슨게임즈\",\n" +
                                            "    \"chart\": [\n" +
                                            "      {\n" +
                                            "        \"x\": \"2014/01/01\",\n" +
                                            "        \"y\": 50000\n" +
                                            "      }\n" +
                                            "    ],\n" +
                                            "    \"news\": [\n" +
                                            "      {\n" +
                                            "        \"title\": \"넥슨게임즈 (2014) - 하락\",\n" +
                                            "        \"url\": \"https://example.com/news/1\",\n" +
                                            "        \"publisher\": \"신문사 B\",\n" +
                                            "        \"date\": \"2014/01/01\"\n" +
                                            "      }\n" +
                                            "    ]\n" +
                                            "  }\n" +
                                            "]"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 후 이용 가능",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "종목 정보를 조회할 수 없습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "JSON->Object 역직렬화에 실패했습니다",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    ResponseEntity<List<GameResultStockDto>> showResultStockList();

    @Operation(
            summary = "게임 결과 저장",
            description = "게임의 최종 결과를 DB에 저장"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    ResponseEntity<?> saveRanking();

    @Operation(
            summary = "게임 랭킹 리스트 조회",
            description = "게임의 최종 금액을 기준으로 내림차순 정렬된 리스트 반환 (10위까지)"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    ResponseEntity<List<GameRankingDto>> showRanking();

    @Operation(
            summary = "userId=7의 진행중인 게임 삭제(redis)",
            description = "테스트 중 게임 초기화시 사용, userId는 7로 고정"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200"),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버에 오류가 발생하였습니다.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    ResponseEntity<?> stopGame();
}