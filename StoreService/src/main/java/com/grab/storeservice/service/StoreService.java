package com.grab.storeservice.service;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.grab.storeservice.exception.AllreadyExistingStoreException;
import com.grab.storeservice.exception.ProductAlreadyExists;
import com.grab.storeservice.exception.ProductNotFound;
import com.grab.storeservice.exception.QuantityLessInStore;
import com.grab.storeservice.exception.StoreNotFoundException;
import com.grab.storeservice.model.Body;
import com.grab.storeservice.model.Product;
import com.grab.storeservice.model.Store;
import com.grab.storeservice.repo.StoreRepo;

import java.util.List;
import java.util.Optional;

@Service
public class StoreService implements IService {

    @Autowired
    private StoreRepo srepo;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public Store addStore(Store s, int gstId) throws AllreadyExistingStoreException {
        Optional<Store> opt = srepo.findByGstId(gstId);
        if (opt.isPresent()) {
            throw new AllreadyExistingStoreException("Store already present");
        }
        Store addstore = srepo.save(s);
        return addstore;
    }

    @Override
    public List<Store> getAllStores() {
        return srepo.findAll();
    }

    @Override
    public boolean deleteStore(int gstId) throws StoreNotFoundException {
        Optional<Store> opt = srepo.findByGstId(gstId);
        if (opt.isPresent()) {
            srepo.deleteByGstId(gstId);
            return true;
        } else {
            throw new StoreNotFoundException("No store found");
        }
    }

    @Override
    public boolean addProductByGstId(Product p, int gstId) throws StoreNotFoundException, ProductAlreadyExists {
        Store ss;
        boolean status = false;
        if (srepo.findByGstId(gstId).isPresent()) {
            ss = srepo.findByGstId(gstId).get();

            List<Product> ss1 = ss.getProducts();
            if (ss1.size() == 0) {
                ss1.add(p);
                ss.setProducts(ss1);
                srepo.save(ss);
                status = true;
                return true;
            } else {
                for (Product x : ss1) {
                    if (x.getPname().equals(p.getPname())) {
                        status = true;
                    }
                }
                if (status) {
                    throw new ProductAlreadyExists("Same product already exists, you can't add");
                } else {
                    ss1.add(p);
                    ss.setProducts(ss1);
                    srepo.save(ss);
                    return true;
                }
            }
        } else {
            throw new StoreNotFoundException("No store found");
        }
    }

    @Override
    public Store updateStore(int gstId, Store s) throws StoreNotFoundException {
        Optional<Store> existingStore = srepo.findByGstId(gstId);
        if (existingStore.isPresent()) {
            Store ex1 = existingStore.get();
            ex1.setStorename(s.getStorename());
            ex1.setProducts(s.getProducts());
            srepo.save(ex1);
            return ex1;
        } else {
            throw new StoreNotFoundException("The store does not exist");
        }
    }

    @Override
    public boolean deleteProduct(int gstId, String pname) throws StoreNotFoundException, ProductNotFound {
        Store existingStore = srepo.findByGstId(gstId).orElseThrow(() -> new StoreNotFoundException("No store found"));
        List<Product> products = existingStore.getProducts();
        boolean exist = products.removeIf(p -> p.getPname().equals(pname));
        if (exist) {
            existingStore.setProducts(products);
            srepo.save(existingStore);
            return true;
        } else {
            throw new ProductNotFound("No product found");
        }
    }

    @Override
    public Store updateProduct(int gstId, String pname, Product p) throws ProductNotFound, StoreNotFoundException {
        Store existingStore = srepo.findByGstId(gstId).orElseThrow(() -> new StoreNotFoundException("Store not found"));
        List<Product> products = existingStore.getProducts();
        boolean exist = products.removeIf(prod -> prod.getPname().equalsIgnoreCase(pname));
        if (exist) {
            products.add(p);
            existingStore.setProducts(products);
            srepo.save(existingStore);
            return existingStore;
        } else {
            throw new ProductNotFound("No such product found");
        }
    }

    @Override
    public List<Product> showProducts(int gstId) throws StoreNotFoundException {
        Store existingStore = srepo.findByGstId(gstId).orElseThrow(() -> new StoreNotFoundException("The store gstId does not exist"));
        return existingStore.getProducts();
    }

    @Override
    public Store showBestDiscount(String pname) {
        double bestDiscount = 0.0;
        Store returnStore = new Store();
        List<Store> existingStores = srepo.findAll();
        for (Store s : existingStores) {
            List<Product> products = s.getProducts();
            for (Product p : products) {
                if (p.getPname().equals(pname)) {
                    if (p.getDiscount() > bestDiscount) {
                        bestDiscount = p.getDiscount();
                        returnStore = s;
                    }
                }
            }
        }
        return returnStore;
    }

    @Override
    public void addProductToCart(int gstId, String pname, double qty) throws StoreNotFoundException, ProductNotFound, QuantityLessInStore {
        Store existingStore = srepo.findByGstId(gstId).orElseThrow(() -> new StoreNotFoundException("Store not found"));
        List<Product> products = existingStore.getProducts();
        Body body = new Body();
        body.setGstId(gstId);
        body.setStorename(existingStore.getStorename());
        Product cartProduct = new Product();
        boolean st = false;

        for (Product currentProduct : products) {
            if (currentProduct.getPname().equals(pname) && currentProduct.getUnit() >= qty) {
                cartProduct.setPname(currentProduct.getPname());
                cartProduct.setUnit(qty);
                cartProduct.setPrice(currentProduct.getPrice());
                cartProduct.setDiscount(currentProduct.getDiscount());
                currentProduct.setUnit(currentProduct.getUnit() - qty);
                body.setProduct(cartProduct);
                body.setTotalPrice(qty * (cartProduct.getPrice()) * (100 - (cartProduct.getDiscount())) * 0.01);
                updateProduct(gstId, pname, currentProduct);

                // Convert Body object to JSON and send via RestTemplate
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                HttpEntity<Body> request = new HttpEntity<>(body, headers);
                restTemplate.exchange("http://external-service-url/api/cart/add", HttpMethod.POST, request, String.class);

                st = true;
                break;
            }
        }
        if (!st) {
            throw new QuantityLessInStore("Quantity shortage");
        }
    }

    @Override
    public void delProductToCart(int gstId, String pname, double qty) throws StoreNotFoundException, ProductNotFound {
        Store existingStore = srepo.findByGstId(gstId).orElseThrow(() -> new StoreNotFoundException("Store not found"));
        List<Product> products = existingStore.getProducts();
        Body body = new Body();
        body.setGstId(gstId);
        body.setStorename(existingStore.getStorename());
        Product cartProduct = new Product();
        boolean st = false;

        for (Product currentProduct : products) {
            if (currentProduct.getPname().equals(pname) && currentProduct.getUnit() >= qty) {
                cartProduct.setPname(currentProduct.getPname());
                cartProduct.setUnit(qty);
                cartProduct.setPrice(currentProduct.getPrice());
                cartProduct.setDiscount(currentProduct.getDiscount());

                currentProduct.setUnit(currentProduct.getUnit() + qty);
                body.setProduct(cartProduct);
                body.setTotalPrice(qty * (cartProduct.getPrice()) * (100 - (cartProduct.getDiscount())) * 0.01);
                updateProduct(gstId, pname, currentProduct);

                // Convert Body object to JSON and send via RestTemplate
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                HttpEntity<Body> request = new HttpEntity<>(body, headers);
                restTemplate.exchange("http://external-service-url/api/cart/delete", HttpMethod.POST, request, String.class);

                st = true;
                break;
            }
        }
        if (!st) {
            throw new ProductNotFound("Product not found in cart");
        }
    }

	
}
