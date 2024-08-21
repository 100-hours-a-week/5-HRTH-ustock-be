package com.hrth.ustock.controller;

import com.hrth.ustock.dto.holding.HoldingRequestDto;
import com.hrth.ustock.dto.portfolio.PortfolioListDto;
import com.hrth.ustock.dto.portfolio.PortfolioRequestDto;
import com.hrth.ustock.dto.portfolio.PortfolioResponseDto;
import com.hrth.ustock.exception.HoldingNotFoundException;
import com.hrth.ustock.exception.PortfolioNotFoundException;
import com.hrth.ustock.exception.StockNotFoundException;
import com.hrth.ustock.exception.UserNotFoundException;
import com.hrth.ustock.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/portfolio")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private static final long tempUserId = 7L;

    // 7. 포트폴리오 생성
    @PostMapping
    public ResponseEntity<?> createPortfolio(@RequestBody PortfolioRequestDto portfolioRequestDTO) {

        // TODO: 스프링 시큐리티로 유저 아이디 받아서 넘기기 userId
        try {
            portfolioService.addPortfolio(portfolioRequestDTO, tempUserId);
            return ResponseEntity.ok().build();
        } catch (UserNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 8. 포트폴리오 리스트 조회
    @GetMapping
    public ResponseEntity<?> showPortfolioList() {

        // TODO: 스프링 시큐리티로 유저 아이디 받아서 넘기기 userId
        try {
            PortfolioListDto list = portfolioService.getPortfolioList(tempUserId);
            return ResponseEntity.ok().body(list);
        } catch (PortfolioNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 9. 개별 포트폴리오 조회
    @GetMapping("/{pfId}")
    public ResponseEntity<?> showPortfolioById(@PathVariable("pfId") Long pfId) {

        try {
            PortfolioResponseDto portfolio = portfolioService.getPortfolio(pfId);
            return ResponseEntity.ok().body(portfolio);
        } catch (PortfolioNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{pfId}/holding/{code}")
    public ResponseEntity<?> buyPortfolioStock(
            @PathVariable("pfId") Long pfId, @PathVariable("code") String code, @RequestBody HoldingRequestDto holdingRequestDto) {

        try {
            portfolioService.buyStock(pfId, code, holdingRequestDto);
        } catch (PortfolioNotFoundException | StockNotFoundException e) {
            String message = e instanceof PortfolioNotFoundException ?
                    "포트폴리오 정보를 찾을 수 없습니다." :
                    "주식 정보를 찾을 수 없습니다.";

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message);
        }

        return ResponseEntity.ok().build();
    }

    // 10. 개별 종목 추가 매수
    @PatchMapping("/{pfId}/holding/{code}")
    public ResponseEntity<?> buyAdditionalPortfolioStock(
            @PathVariable("pfId") Long pfId, @PathVariable("code") String code, @RequestBody HoldingRequestDto holdingRequestDto) {

        if (holdingRequestDto.getQuantity() <= 0 || holdingRequestDto.getPrice() <= 0) {
            return ResponseEntity.badRequest().build();
        }
        try {
            portfolioService.additionalBuyStock(pfId, code, holdingRequestDto);
            return ResponseEntity.ok().build();
        } catch (PortfolioNotFoundException | StockNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 11. 개별 종목 수정
    @PutMapping("/{pfId}/holding/{code}")
    public ResponseEntity<?> editPortfolioStock(
            @PathVariable("pfId") Long pfId, @PathVariable("code") String code, @RequestBody HoldingRequestDto holdingRequestDto) {

        if (holdingRequestDto.getQuantity() < 0 || holdingRequestDto.getPrice() < 0) {
            return ResponseEntity.badRequest().build();
        }

        try {
            portfolioService.editHolding(pfId, code, holdingRequestDto);
        } catch (HoldingNotFoundException | PortfolioNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok().build();
    }

    // 12. 개별 종목 삭제
    @DeleteMapping("/{pfId}/holding/{code}")
    public ResponseEntity<?> deletePortfolioStock(@PathVariable("pfId") Long pfId, @PathVariable("code") String code) {

        try {
            portfolioService.deleteHolding(pfId, code);
        } catch (HoldingNotFoundException | PortfolioNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok().build();
    }

    // 13. 포트폴리오 삭제
    @DeleteMapping("/{pfId}")
    public ResponseEntity<?> deletePortfolio(@PathVariable("pfId") Long pfId) {

        try {
            portfolioService.deletePortfolio(pfId);
        } catch (PortfolioNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok().build();
    }
}
