package am.foodme.backend.service;

import am.foodme.backend.dto.OrderDto;
import am.foodme.backend.dto.OrderReviewRequestDto;
import am.foodme.backend.exceptionHandler.BadRequestException;
import am.foodme.backend.exceptionHandler.NotFoundException;
import am.foodme.backend.model.Chef;
import am.foodme.backend.model.Order;
import am.foodme.backend.model.OrderReview;
import am.foodme.backend.repository.ChefRepository;
import am.foodme.backend.repository.OrderRepository;
import am.foodme.backend.repository.OrderReviewRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** KAN-9: customers rate their delivered orders, and each chef's rating is the average. */
@Service
public class OrderReviewService {

    static final String ONLY_DELIVERED = "Only delivered orders can be reviewed.";
    static final String ALREADY_REVIEWED = "Order already reviewed.";

    private final OrderRepository orderRepository;
    private final OrderReviewRepository orderReviewRepository;
    private final ChefRepository chefRepository;
    private final MeterRegistry meterRegistry;

    public OrderReviewService(OrderRepository orderRepository, OrderReviewRepository orderReviewRepository,
                              ChefRepository chefRepository, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.orderReviewRepository = orderReviewRepository;
        this.chefRepository = chefRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Saves the customer's rating of their own delivered order and recalculates
     * the chef's rating. Someone else's order is reported as not found, so order
     * numbers can't be probed.
     */
    @Transactional
    public OrderDto reviewForCustomer(String number, String customerEmail, OrderReviewRequestDto request) {
        String email = customerEmail == null ? "" : customerEmail.trim().toLowerCase();
        Order order = orderRepository.findByNumber(number)
                .filter(o -> o.getCustomer() != null && email.equalsIgnoreCase(o.getCustomer().getEmail()))
                .orElseThrow(() -> new NotFoundException("Order " + number + " not found"));

        if (!"DELIVERED".equals(order.getStatus())) {
            throw new BadRequestException(ONLY_DELIVERED);
        }
        if (order.getReview() != null || orderReviewRepository.existsByOrderId(order.getId())) {
            throw new BadRequestException(ALREADY_REVIEWED);
        }

        OrderReview review = new OrderReview();
        review.setOrder(order);
        review.setCustomer(order.getCustomer());
        review.setChef(order.getChef());
        review.setRating(request.getRating().intValueExact());
        String comment = request.getComment() == null ? null : request.getComment().trim();
        review.setComment(comment == null || comment.isEmpty() ? null : comment);
        review.setCreatedAt(LocalDateTime.now());
        try {
            orderReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // Two submissions raced; the unique order_id constraint let only one through.
            throw new BadRequestException(ALREADY_REVIEWED);
        }
        order.setReview(review);

        Chef chef = order.getChef();
        chef.setRating(roundToOneDecimal(orderReviewRepository.averageRatingForChef(chef.getId())));
        chefRepository.save(chef);

        meterRegistry.counter("foodme.orders.reviewed").increment();
        return OrderDto.mapEntityToDto(order);
    }

    static Double roundToOneDecimal(Double average) {
        if (average == null) {
            return null;
        }
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
