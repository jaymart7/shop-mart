package com.example.shopmart.data.repository.cart

import com.example.shopmart.data.model.Cart
import com.example.shopmart.data.model.Product
import com.example.shopmart.util.NAME
import com.example.shopmart.util.PRICE
import com.example.shopmart.util.QUANTITY
import com.example.shopmart.util.getCartReference
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CartRepositoryImpl @Inject constructor(
    private val firebaseFirestore: FirebaseFirestore,
    private val productReference: CollectionReference
) : CartRepository {

    override suspend fun addToCart(cart: Cart) {
        return withContext(Dispatchers.IO) {
            suspendCoroutine<Unit> { continuation ->
                val cartReference = getCartReference(firebaseFirestore)
                if (cartReference != null) {
                    cartReference.document(cart.productId)
                        .set(cart)
                        .addOnSuccessListener {
                            continuation.resume(Unit)
                        }
                        .addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                } else {
                    continuation.resumeWithException(Exception("Not logged in"))
                }
            }
        }
    }

    override suspend fun getCartList(): List<Cart> {
        return withContext(Dispatchers.IO) {
            suspendCoroutine<List<Cart>> { continuation ->
                runCatching {
                    getMyCart { cartList ->
                        getProductListByIds(cartList) { continuation.resume(cartList) }
                    }
                }.onFailure {
                    continuation.resumeWithException(it)
                }
            }
        }
    }

    private fun getMyCart(success: (cartList: MutableList<Cart>) -> Unit) {
        val cartReference = getCartReference(firebaseFirestore)
        if (cartReference != null) {
            cartReference.get()
                .addOnSuccessListener { querySnapshot ->
                    val cartList = mutableListOf<Cart>()
                    for (cartSnapshot in querySnapshot) {
                        cartList.add(
                            Cart(
                                cartSnapshot.id,
                                cartSnapshot.data[QUANTITY] as Long
                            )
                        )
                    }
                    success(cartList)
                }
                .addOnFailureListener {
                    throw Exception(it)
                }
        } else {
            throw Exception("Not logged in")
        }
    }

    private fun getProductListByIds(cartList: MutableList<Cart>, success: () -> Unit) {
        val productIds = cartList.map { it.productId }
        productReference.whereIn(FieldPath.documentId(), productIds).get()
            .addOnSuccessListener { productSnapshot ->
                for ((i, productDocument) in productSnapshot.withIndex()) {
                    cartList[i].product = Product(
                        productDocument.id,
                        productDocument.data[NAME] as String,
                        productDocument.data[PRICE] as Long
                    )
                }
                success()
            }
            .addOnFailureListener {
                throw Exception(it)
            }
    }
}