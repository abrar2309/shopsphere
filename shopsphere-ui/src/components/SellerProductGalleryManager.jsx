import { useEffect, useState } from 'react';
import {
    deleteProductImage,
    getProductImages,
    setPrimaryProductImage,
    uploadProductImages,
} from '../api/productGalleryApi';
import { getProductImageUrl } from '../api/productApi';

import './SellerProductGalleryManager.css';

export default function SellerProductGalleryManager({ productId }) {
    const [images, setImages] = useState([]);
    const [files, setFiles] = useState([]);
    const [loading, setLoading] = useState(true);
    const [uploading, setUploading] = useState(false);
    const [actionId, setActionId] = useState(null);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');

    async function refresh() {
        setLoading(true);
        setError('');

        try {
            const data = await getProductImages(productId);
            setImages(Array.isArray(data) ? data : []);
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                'Could not load product images.'
            );
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        refresh();
    }, [productId]);

    async function handleUpload(event) {
        event.preventDefault();
        setMessage('');
        setError('');

        if (!files.length) {
            setError('Choose at least one image.');
            return;
        }

        setUploading(true);

        try {
            await uploadProductImages(productId, files);
            setFiles([]);
            setMessage('Product images uploaded successfully.');
            await refresh();
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                requestError.message ||
                'Image upload failed.'
            );
        } finally {
            setUploading(false);
        }
    }

    async function handlePrimary(imageId) {
        setActionId(imageId);
        setError('');
        setMessage('');

        try {
            await setPrimaryProductImage(productId, imageId);
            setMessage('Primary image updated.');
            await refresh();
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                'Could not update the primary image.'
            );
        } finally {
            setActionId(null);
        }
    }

    async function handleDelete(imageId) {
        if (!window.confirm('Delete this product image?')) return;

        setActionId(imageId);
        setError('');
        setMessage('');

        try {
            await deleteProductImage(productId, imageId);
            setMessage('Product image deleted.');
            await refresh();
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                'Could not delete this image.'
            );
        } finally {
            setActionId(null);
        }
    }

    return (
        <section className="seller-gallery-manager">
            <h3>Product image gallery</h3>
            <p>Upload up to 10 images. Choose which image customers see first.</p>

            <form onSubmit={handleUpload}>
                <input
                    type="file"
                    accept="image/png,image/jpeg"
                    multiple
                    onChange={(event) => {
                        setFiles(Array.from(event.target.files || []));
                        setError('');
                    }}
                />

                {files.length > 0 && (
                    <p>{files.length} image(s) selected</p>
                )}

                <button type="submit" disabled={uploading || files.length === 0}>
                    {uploading ? 'Uploading...' : 'Upload images'}
                </button>
            </form>

            {message && <p className="seller-gallery-success">{message}</p>}
            {error && <p className="seller-gallery-error" role="alert">{error}</p>}

            {loading ? (
                <p>Loading images...</p>
            ) : images.length === 0 ? (
                <p>No gallery images uploaded yet.</p>
            ) : (
                <div className="seller-gallery-grid">
                    {images.map((image) => (
                        <article key={image.id}>
                            <img
                                src={getProductImageUrl(image.imageUrl)}
                                alt="Product"
                            />

                            {image.primary && (
                                <strong className="seller-gallery-primary">
                                    Primary image
                                </strong>
                            )}

                            <button
                                type="button"
                                disabled={image.primary || actionId !== null}
                                onClick={() => handlePrimary(image.id)}
                            >
                                {actionId === image.id
                                    ? 'Updating...'
                                    : 'Set as primary'}
                            </button>

                            <button
                                type="button"
                                className="seller-gallery-delete"
                                disabled={actionId !== null}
                                onClick={() => handleDelete(image.id)}
                            >
                                Delete image
                            </button>
                        </article>
                    ))}
                </div>
            )}
        </section>
    );
}